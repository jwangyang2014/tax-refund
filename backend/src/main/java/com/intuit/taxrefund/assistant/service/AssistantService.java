package com.intuit.taxrefund.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.Action;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.ActionType;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.Confidence;
import com.intuit.taxrefund.assistant.infra.AssistantProps;
import com.intuit.taxrefund.assistant.infra.AssistantQuotaService;
import com.intuit.taxrefund.assistant.infra.ConversationStateStore;
import com.intuit.taxrefund.assistant.infra.PrivacyFilter;
import com.intuit.taxrefund.assistant.model.*;
import com.intuit.taxrefund.assistant.nlp.IntentClassifier;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.llm.LlmClientRouter;
import com.intuit.taxrefund.refund.controller.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.service.RefundService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AssistantService {

    private static final Logger log = LogManager.getLogger(AssistantService.class);

    // Rotated so the user doesn't see the exact same wording on every off-topic message
    private static final List<String> OFF_TOPIC_REPLIES = List.of(
        "I'm here to help with your tax refund. Try asking: \"What's my refund status?\","
            + " \"When will I get my refund?\", or \"Why is my refund delayed?\"",
        "That's outside what I can help with right now — I specialise in tax refund questions.",
        "I didn't quite catch that in the context of your refund. Try asking about your"
            + " status, estimated arrival date, or what to do if your refund is delayed."
    );

    private final RefundService          refundService;
    private final PrivacyFilter          privacyFilter;
    private final IntentClassifier       classifier;
    private final AssistantProps         props;
    private final AssistantQuotaService  quota;
    private final AssistantPlanner       planner;
    private final ConversationStateStore stateStore;
    private final PolicySnippets         policySnippets;
    private final LlmClientRouter        llmRouter;
    private final ObjectMapper           om;

    public AssistantService(
        RefundService refundService, PrivacyFilter privacyFilter,
        IntentClassifier classifier, AssistantProps props,
        AssistantQuotaService quota, AssistantPlanner planner,
        ConversationStateStore stateStore, PolicySnippets policySnippets,
        LlmClientRouter llmRouter, ObjectMapper om
    ) {
        this.refundService  = refundService; this.privacyFilter  = privacyFilter;
        this.classifier     = classifier;    this.props          = props;
        this.quota          = quota;         this.planner        = planner;
        this.stateStore     = stateStore;    this.policySnippets = policySnippets;
        this.llmRouter      = llmRouter;     this.om             = om;
    }

    public AssistantChatResponse answer(JwtService.JwtPrincipal principal, String question) {
        long userId = principal.userId();

        // 1. Load context
        ConversationContext ctx = stateStore.get(userId);

        // 2. Classify intent
        IntentClassifier.IntentResult r = classifier.classify(question);
        AssistantIntent intent   = r.intent();
        boolean isLowNlpConf     = intent == AssistantIntent.UNKNOWN || r.confidence() < 0.55;

        log.info("assistant_intent userId={} intent={} confidence={} model={} lowConf={}",
            userId, intent, r.confidence(), r.model(), isLowNlpConf);

        // 3. Fetch refund data & plan
        RefundStatusResponse refund = refundService.getLatestRefundStatus(principal, null);
        AssistantPlan plan = planner.plan(ctx, intent, r.confidence(), refund.status());

        log.info("assistant_plan userId={} nextState={} escalate={} offTopic={} repeat={} gated={}",
            userId, plan.nextState(), plan.escalate(), plan.offTopic(),
            plan.repeatHint(), plan.stateGated());

        // ── 4. Off-topic short-circuit ────────────────────────────────────────
        // No LLM call, no state change, no quota consumed.
        // Still persists the turn so repeated off-topic spam can escalate.
        if (plan.offTopic()) {
            log.info("assistant_off_topic userId={} confirmedState={}", userId, ctx.confirmedState());
            String reply = OFF_TOPIC_REPLIES.get(ctx.turnCount() % OFF_TOPIC_REPLIES.size());
            stateStore.set(userId, ctx.advance(
                ctx.confirmedState(), AssistantIntent.OFF_TOPIC,
                true, "LOW", question, reply));
            return new AssistantChatResponse(reply, List.of(),
                List.of(new Action(ActionType.REFRESH, "Refresh status")), Confidence.LOW);
        }

        // 5. Citations and actions
        List<AssistantChatResponse.Citation> citations = plan.includePolicySnippets()
            ? policySnippets.forStatus(refund.status()) : List.of();
        List<Action> actions = buildActions(refund, plan.escalate());

        // 6. LLM call
        AssistantChatResponse response = callLlm(
            userId, question, ctx, refund, citations, actions, plan);

        // 7. Gate state advancement on LLM's own confidence
        String llmConf = (response != null && response.confidence() != null)
            ? response.confidence().name() : "LOW";

        // 8. Commit – stateGated → stay at confirmedState
        ConversationState toCommit = plan.stateGated()
            ? ctx.confirmedState() : plan.nextState();

        ConversationContext updated = ctx.advance(
            toCommit, intent, isLowNlpConf, llmConf, question,
            response != null && response.answerMarkdown() != null
                ? response.answerMarkdown() : "");
        stateStore.set(userId, updated);

        log.info("assistant_ctx_committed userId={} confirmedState={} stateGated={} llmConf={}",
            userId, updated.confirmedState(), plan.stateGated(), llmConf);

        return response != null
            ? response : buildDeterministicFallback(question, refund, citations, actions);
    }

    // ── LLM orchestration ────────────────────────────────────────────────────

    private AssistantChatResponse callLlm(
        long userId, String question, ConversationContext ctx,
        RefundStatusResponse refund, List<AssistantChatResponse.Citation> citations,
        List<Action> actions, AssistantPlan plan
    ) {
        Map<String, Object> authData = privacyFilter.buildAuthoritativeDataForLlm(refund, plan);
        authData.put("policies", citations);
        String devPrompt  = buildDeveloperPrompt(plan.escalate(), plan.repeatHint());
        String userPrompt = buildUserPrompt(question, ctx, authData);
        Map<String, Object> schema = responseSchema();

        var primary = llmRouter.primary();
        if (!primary.isAvailable() || "mock".equalsIgnoreCase(primary.provider())) {
            return parseOrFallback(llmRouter.callWithFallback(devPrompt, userPrompt, schema),
                actions, userId, primary.provider());
        }
        if (!quota.tryConsumeDaily(userId, props.dailyOpenAiCallsPerUser())) {
            log.warn("assistant_quota_exceeded userId={}", userId);
            return parseOrFallback(llmRouter.callWithFallback(devPrompt, userPrompt, schema),
                actions, userId, "quota-fallback");
        }
        try {
            String json = llmRouter.callWithFallback(devPrompt, userPrompt, schema);
            log.info("assistant_llm_call_ok userId={} chars={}", userId, json == null ? 0 : json.length());
            return parseOrFallback(json, actions, userId, primary.provider());
        } catch (Exception e) {
            log.error("assistant_llm_call_failed userId={} err={}", userId, e.toString());
            return buildDeterministicFallback(question, refund, citations, actions);
        }
    }

    // ── Prompt builders ──────────────────────────────────────────────────────

    private String buildDeveloperPrompt(boolean escalate, boolean repeatHint) {
        String base = "You are a TurboTax-like assistant helping users understand their tax refund.\n"
            + "STRICT RULES:\n"
            + "- Only use facts present in authoritativeData. Never invent numbers or dates.\n"
            + "- Do NOT request or reveal PII (SSN, bank account, address, full name).\n"
            + "- Do NOT mention internal tracking IDs.\n"
            + "- Return ONLY valid JSON matching the provided schema.\n"
            + "CONFIDENCE RATING (self-assess honestly):\n"
            + "- HIGH   : authoritativeData fully answers the question with specific facts.\n"
            + "- MEDIUM : authoritativeData partially answers; some info is general.\n"
            + "- LOW    : you cannot give a complete, specific answer from the data provided.\n";
        if (repeatHint) {
            base += "\nREPEAT NOTICE: The user has asked a very similar question before and your "
                + "previous answer did not satisfy them. Do NOT repeat the same explanation. "
                + "Try a noticeably different approach: simplify the language, break into smaller "
                + "steps, or acknowledge what you do not know and suggest concrete next actions.\n";
        }
        if (escalate) {
            base += "\nESCALATION: The user has been stuck for several turns. Empathetically "
                + "acknowledge their frustration, summarise the refund facts you have, and clearly "
                + "recommend they contact a human support agent. Include CONTACT_SUPPORT in actions.\n";
        }
        return base;
    }

    private String buildUserPrompt(String question, ConversationContext ctx,
                                   Map<String, Object> authData) {
        StringBuilder sb = new StringBuilder();
        if (!ctx.history().isEmpty()) {
            sb.append("Conversation history (oldest first):\n");
            for (ConversationContext.HistoryEntry e : ctx.history()) {
                sb.append("User: ").append(e.question()).append("\n");
                sb.append("Assistant: ").append(e.answer()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("Question: ").append(question).append("\n\n");
        sb.append("authoritativeData:\n").append(safeJson(authData));
        return sb.toString();
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private AssistantChatResponse parseOrFallback(String json, List<Action> defaultActions,
                                                   long userId, String provider) {
        AssistantChatResponse parsed;
        try { parsed = parseStrict(json); }
        catch (Exception e) {
            log.warn("assistant_llm_bad_output userId={} provider={} err={}", userId, provider, e.toString());
            return null;
        }
        if (parsed == null) return null;
        if (parsed.answerMarkdown() == null || parsed.answerMarkdown().isBlank()) {
            try {
                String raw = om.readTree(json).path("answerMarkdown").asText("");
                if (!raw.isBlank()) {
                    parsed = new AssistantChatResponse(raw,
                        parsed.citations()  == null ? List.of() : parsed.citations(),
                        parsed.actions()    == null ? defaultActions : parsed.actions(),
                        parsed.confidence() == null ? Confidence.MEDIUM : parsed.confidence());
                } else return null;
            } catch (Exception e) {
                log.warn("assistant_llm_answer_recovery_failed userId={} err={}", userId, e.toString());
                return null;
            }
        }
        return new AssistantChatResponse(
            parsed.answerMarkdown(),
            parsed.citations()  == null ? List.of() : parsed.citations(),
            mergeActions(parsed.actions() == null ? List.of() : parsed.actions(), defaultActions),
            parsed.confidence() == null ? Confidence.MEDIUM : parsed.confidence());
    }

    private AssistantChatResponse buildDeterministicFallback(
        String question, RefundStatusResponse refund,
        List<AssistantChatResponse.Citation> citations, List<Action> actions
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Your question:** ").append(question).append("\n\n")
          .append("**Latest refund status:** ").append(refund.status()).append("\n")
          .append("**Tax year:** ").append(refund.taxYear()).append("\n")
          .append("**Last updated:** ").append(refund.lastUpdatedAt()).append("\n");
        if (refund.availableAtEstimated() != null
            && !com.intuit.taxrefund.refund.model.RefundStatus.AVAILABLE.name().equals(refund.status()))
            sb.append("**Estimated availability:** ").append(refund.availableAtEstimated()).append("\n");
        Confidence c = refund.availableAtEstimated() != null ? Confidence.MEDIUM : Confidence.LOW;
        if (com.intuit.taxrefund.refund.model.RefundStatus.AVAILABLE.name().equals(refund.status()))
            c = Confidence.HIGH;
        return new AssistantChatResponse(sb.toString(), citations, actions, c);
    }

    private AssistantChatResponse parseStrict(String json) {
        try { return om.readValue(json, AssistantChatResponse.class); }
        catch (Exception e) { throw new IllegalArgumentException("LLM parse failed: " + e.getMessage()); }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private static List<Action> buildActions(RefundStatusResponse refund, boolean escalate) {
        List<Action> out = new ArrayList<>();
        out.add(new Action(ActionType.REFRESH, "Refresh status"));
        if (refund.trackingId() != null && !refund.trackingId().isBlank())
            out.add(new Action(ActionType.SHOW_TRACKING, "Show tracking details"));
        if (escalate)
            out.add(new Action(ActionType.CONTACT_SUPPORT, "Talk to a human agent"));
        else if ("REJECTED".equals(refund.status()))
            out.add(new Action(ActionType.CONTACT_SUPPORT, "Contact support"));
        else if ("PROCESSING".equals(refund.status()))
            out.add(new Action(ActionType.CONTACT_SUPPORT, "Contact support if no update in 21 days"));
        return out;
    }

    private static List<Action> mergeActions(List<Action> llm, List<Action> defaults) {
        List<Action> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Action a : llm) {
            if (a == null || a.type() == null) continue;
            if (seen.add(a.type().name() + "|" + (a.label() == null ? "" : a.label().trim()))) out.add(a);
        }
        for (Action a : defaults) {
            if (a == null || a.type() == null) continue;
            if (out.stream().anyMatch(x -> x.type() == a.type())) continue;
            if (seen.add(a.type().name() + "|" + (a.label() == null ? "" : a.label().trim()))) out.add(a);
        }
        return out;
    }

    private String safeJson(Object o) {
        try { return om.writerWithDefaultPrettyPrinter().writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "assistant_response");
        schema.put("strict", true);
        schema.put("schema", Map.of(
            "type", "object", "additionalProperties", false,
            "properties", Map.of(
                "answerMarkdown", Map.of("type", "string"),
                "citations", Map.of("type", "array", "items", Map.of(
                    "type", "object", "additionalProperties", false,
                    "properties", Map.of(
                        "docId",  Map.of("type", "string"),
                        "quote",  Map.of("type", "string")),
                    "required", List.of("docId", "quote"))),
                "actions", Map.of("type", "array", "items", Map.of(
                    "type", "object", "additionalProperties", false,
                    "properties", Map.of(
                        "type",  Map.of("type", "string",
                                        "enum", List.of("REFRESH","CONTACT_SUPPORT","SHOW_TRACKING")),
                        "label", Map.of("type", "string")),
                    "required", List.of("type", "label"))),
                "confidence", Map.of("type","string","enum", List.of("LOW","MEDIUM","HIGH"))),
            "required", List.of("answerMarkdown","citations","actions","confidence")));
        return schema;
    }
}
