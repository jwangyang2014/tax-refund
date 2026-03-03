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
import com.intuit.taxrefund.llm.LlmClient;
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

    // ── Main entry point ─────────────────────────────────────────────────────

    public AssistantChatResponse answer(JwtService.JwtPrincipal principal, String question) {
        long userId = principal.userId();

        // 1. Load full conversation context (state + counters + history) from Redis
        ConversationContext ctx = stateStore.get(userId);

        // 2. Classify intent
        IntentClassifier.IntentResult r = classifier.classify(question);
        AssistantIntent intent  = r.intent();
        boolean isLowConf = intent == AssistantIntent.UNKNOWN || r.confidence() < 0.55;

        log.info("assistant_intent userId={} intent={} confidence={} model={} lowConf={}",
            userId, intent, r.confidence(), r.model(), isLowConf);

        // 3. Fetch live refund status
        RefundStatusResponse refund = refundService.getLatestRefundStatus(principal, null);

        // 4. Plan: chooses nextState, escalation flag, data to include
        AssistantPlan plan = planner.plan(ctx, intent, r.confidence(), refund.status());

        log.info("assistant_plan userId={} nextState={} escalate={} troubleshootingTurns={} lowConfTurns={}",
            userId, plan.nextState(), plan.escalate(),
            ctx.troubleshootingTurns(), ctx.lowConfidenceTurns());

        // 5. Build citations and actions
        List<AssistantChatResponse.Citation> citations = plan.includePolicySnippets()
            ? policySnippets.forStatus(refund.status()) : List.of();
        List<Action> actions = buildActions(refund, plan.escalate());

        // 6. Call LLM
        AssistantChatResponse response = callLlm(userId, question, ctx, refund, citations, actions, plan);

        // 7. Persist updated context with this turn appended to history
        String botAnswer = (response != null && response.answerMarkdown() != null)
            ? response.answerMarkdown() : "";
        stateStore.set(userId, ctx.advance(plan.nextState(), isLowConf, question, botAnswer));

        return response != null
            ? response
            : buildDeterministicFallback(question, refund, citations, actions);
    }

    // ── LLM orchestration ────────────────────────────────────────────────────

    private AssistantChatResponse callLlm(
        long userId, String question, ConversationContext ctx,
        RefundStatusResponse refund, List<AssistantChatResponse.Citation> citations,
        List<Action> actions, AssistantPlan plan
    ) {
        Map<String, Object> authData = privacyFilter.buildAuthoritativeDataForLlm(refund, plan);
        authData.put("policies", citations);

        String devPrompt  = buildDeveloperPrompt(plan.escalate());
        String userPrompt = buildUserPrompt(question, ctx, authData);
        Map<String, Object> schema = responseSchema();

        LlmClient primary = llmRouter.primary();
        log.info("assistant_llm_primary userId={} provider={} available={}", userId, primary.provider(), primary.isAvailable());

        if (!primary.isAvailable() || "mock".equalsIgnoreCase(primary.provider())) {
            return parseOrFallback(llmRouter.callWithFallback(devPrompt, userPrompt, schema), actions, userId, primary.provider());
        }
        if (!quota.tryConsumeDaily(userId, props.dailyOpenAiCallsPerUser())) {
            log.warn("assistant_quota_exceeded userId={}", userId);
            return parseOrFallback(llmRouter.callWithFallback(devPrompt, userPrompt, schema), actions, userId, "quota-fallback");
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

    private String buildDeveloperPrompt(boolean escalate) {
        String base = """
You are a TurboTax-like assistant. STRICT RULES:
- Only use facts present in authoritativeData. Do NOT invent numbers or dates.
- Do NOT request or reveal PII (SSN, bank account, address, full name).
- Do NOT mention internal tracking IDs.
- Return ONLY JSON matching the provided schema.
""";
        if (escalate) {
            base += """
ESCALATION: The user appears stuck after multiple attempts. Empathetically acknowledge
their frustration, summarise what you know about their refund, and clearly recommend
they contact a human support agent for further help. Include CONTACT_SUPPORT in actions.
""";
        }
        return base;
    }

    /**
     * Injects conversation history so the LLM can produce coherent multi-turn replies.
     * History entries are added above the current question.
     */
    private String buildUserPrompt(String question, ConversationContext ctx, Map<String, Object> authData) {
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

    // ── Response parsing ─────────────────────────────────────────────────────

    private AssistantChatResponse parseOrFallback(String json, List<Action> defaultActions, long userId, String provider) {
        AssistantChatResponse parsed;
        try {
            parsed = parseStrict(json);
        } catch (Exception e) {
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
                && !com.intuit.taxrefund.refund.model.RefundStatus.AVAILABLE.name().equals(refund.status())) {
            sb.append("**Estimated availability:** ").append(refund.availableAtEstimated()).append("\n");
        }
        Confidence c = refund.availableAtEstimated() != null ? Confidence.MEDIUM : Confidence.LOW;
        if (com.intuit.taxrefund.refund.model.RefundStatus.AVAILABLE.name().equals(refund.status())) c = Confidence.HIGH;
        return new AssistantChatResponse(sb.toString(), citations, actions, c);
    }

    private AssistantChatResponse parseStrict(String json) {
        try { return om.readValue(json, AssistantChatResponse.class); }
        catch (Exception e) { throw new IllegalArgumentException("LLM output parse failed: " + e.getMessage()); }
    }

    // ── Action helpers ───────────────────────────────────────────────────────

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

    private static List<Action> mergeActions(List<Action> llmActions, List<Action> defaults) {
        List<Action> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Action a : llmActions) {
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
                    "properties", Map.of("docId", Map.of("type","string"), "quote", Map.of("type","string")),
                    "required", List.of("docId","quote"))),
                "actions", Map.of("type", "array", "items", Map.of(
                    "type", "object", "additionalProperties", false,
                    "properties", Map.of(
                        "type", Map.of("type","string","enum",List.of("REFRESH","CONTACT_SUPPORT","SHOW_TRACKING")),
                        "label", Map.of("type","string")),
                    "required", List.of("type","label"))),
                "confidence", Map.of("type","string","enum",List.of("LOW","MEDIUM","HIGH"))),
            "required", List.of("answerMarkdown","citations","actions","confidence")));
        return schema;
    }
}
