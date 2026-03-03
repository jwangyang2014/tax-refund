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
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.service.RefundService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AssistantService {

    private static final Logger log = LogManager.getLogger(AssistantService.class);

    private final RefundService refundService;
    private final PrivacyFilter privacyFilter;
    private final IntentClassifier classifier;
    private final AssistantProps props;
    private final AssistantQuotaService quota;
    private final AssistantPlanner planner;
    private final ConversationStateStore stateStore;
    private final PolicySnippets policySnippets;

    private final LlmClientRouter llmRouter;
    private final ObjectMapper om;

    public AssistantService(
        RefundService refundService,
        PrivacyFilter privacyFilter,
        IntentClassifier classifier,
        AssistantProps props,
        AssistantQuotaService quota,
        AssistantPlanner planner,
        ConversationStateStore stateStore,
        PolicySnippets policySnippets,
        LlmClientRouter llmRouter,
        ObjectMapper om
    ) {
        this.refundService = refundService;
        this.privacyFilter = privacyFilter;
        this.classifier = classifier;
        this.props = props;
        this.quota = quota;
        this.planner = planner;
        this.stateStore = stateStore;
        this.policySnippets = policySnippets;
        this.llmRouter = llmRouter;
        this.om = om;
    }

    public AssistantChatResponse answer(JwtService.JwtPrincipal principal, String question) {
        long userId = principal.userId();

        ConversationState prev = stateStore.get(userId);
        IntentClassifier.IntentResult r = classifier.classify(question);
        AssistantIntent intent = r.intent();

        log.info("assistant_intent userId={} intent={} confidence={} model={}",
            userId, r.intent(), r.confidence(), r.model());

        RefundStatusResponse refund = refundService.getLatestRefundStatus(principal, null);
        AssistantPlan plan = planner.plan(prev, intent, r.confidence(), refund.status());

        log.info("assistant_plan userId={} intent={} refundStatus={} nextState={} includeRefundStatus={} includeEta={} includePolicySnippets={}",
            userId, intent, refund.status(), plan.nextState(),
            plan.includeRefundStatus(), plan.includeEta(), plan.includePolicySnippets());

        List<AssistantChatResponse.Citation> citations = plan.includePolicySnippets()
            ? policySnippets.forStatus(refund.status())
            : List.of();

        List<Action> actions = buildActions(refund);

        stateStore.set(userId, plan.nextState());

        Map<String, Object> authoritativeData = privacyFilter.buildAuthoritativeDataForLlm(refund, plan);
        authoritativeData.put("policies", citations);

        Map<String, Object> schema = responseSchema();

        String developerPrompt = """
You are a TurboTax-like assistant. STRICT RULES:
- Only use facts (numbers/dates/status) present in authoritativeData. Do NOT invent.
- Do NOT request or reveal PII (SSN, bank account, address, full name).
- Do NOT mention tracking IDs.
- Return ONLY JSON that matches the provided schema.
""";

        String userPrompt = """
Question: %s

authoritativeData:
%s
""".formatted(question, safeJson(authoritativeData));

        LlmClient primary = llmRouter.primary();

        log.info("assistant_llm_primary userId={} provider={} model={} available={}",
            userId, primary.provider(), primary.model(), primary.isAvailable());

        if (!primary.isAvailable() || "mock".equalsIgnoreCase(primary.provider())) {
            AssistantChatResponse mockResp = parseOrFallback(
                llmRouter.callWithFallback(developerPrompt, userPrompt, schema),
                actions,
                userId,
                primary.provider()
            );
            return mockResp != null
                ? mockResp
                : buildDeterministicFallback(question, refund, citations, actions, plan);
        }

        if (!quota.tryConsumeDaily(userId, props.dailyOpenAiCallsPerUser())) {
            log.warn("assistant_quota_exceeded userId={}", userId);
            AssistantChatResponse quotaResp = parseOrFallback(
                llmRouter.callWithFallback(developerPrompt, userPrompt, schema),
                actions,
                userId,
                "quota-fallback"
            );
            return quotaResp != null
                ? quotaResp
                : buildDeterministicFallback(question, refund, citations, actions, plan);
        }

        String json;
        try {
            log.info("assistant_llm_call_start userId={} provider={} model={} intent={}",
                userId, primary.provider(), primary.model(), intent);

            json = llmRouter.callWithFallback(developerPrompt, userPrompt, schema);

            log.info("assistant_llm_call_ok userId={} provider={} chars={}",
                userId, primary.provider(), json == null ? 0 : json.length());

        } catch (Exception e) {
            log.error("assistant_llm_call_failed userId={} provider={} err={}",
                userId, primary.provider(), e.toString());

            return buildDeterministicFallback(question, refund, citations, actions, plan);
        }

        AssistantChatResponse parsed = parseOrFallback(json, actions, userId, primary.provider());
        if (parsed == null) {
            return buildDeterministicFallback(question, refund, citations, actions, plan);
        }

        return parsed;
    }

    private AssistantChatResponse parseOrFallback(
        String json,
        List<Action> defaultActions,
        long userId,
        String provider
    ) {
        AssistantChatResponse parsed;
        try {
            parsed = parseStrict(json);
        } catch (Exception e) {
            log.warn("assistant_llm_bad_output userId={} provider={} err={}",
                userId, provider, e.toString());
            return null;
        }

        if (parsed == null) {
            return null;
        }

        if (parsed.answerMarkdown() == null || parsed.answerMarkdown().isBlank()) {
            try {
                String rawAnswer = om.readTree(json).path("answerMarkdown").asText("");
                if (!rawAnswer.isBlank()) {
                    parsed = new AssistantChatResponse(
                        rawAnswer,
                        parsed.citations() == null ? List.of() : parsed.citations(),
                        parsed.actions() == null ? defaultActions : parsed.actions(),
                        parsed.confidence() == null ? Confidence.MEDIUM : parsed.confidence()
                    );
                } else {
                    return null;
                }
            } catch (Exception e) {
                log.warn("assistant_llm_answer_recovery_failed userId={} provider={} errType={} err={}",
                    userId, provider, e.getClass().getSimpleName(), e.toString());
                return null;
            }
        }

        List<Action> mergedActions = mergeActions(
            parsed.actions() == null ? List.of() : parsed.actions(),
            defaultActions
        );

        return new AssistantChatResponse(
            parsed.answerMarkdown(),
            parsed.citations() == null ? List.of() : parsed.citations(),
            mergedActions,
            parsed.confidence() == null ? Confidence.MEDIUM : parsed.confidence()
        );
    }

    private AssistantChatResponse buildDeterministicFallback(
        String question,
        RefundStatusResponse refund,
        List<AssistantChatResponse.Citation> citations,
        List<Action> actions,
        AssistantPlan plan
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Your question:** ").append(question).append("\n\n");

        if (plan.includeRefundStatus()) {
            sb.append("**Latest refund status:** ").append(refund.status()).append("\n")
                .append("**Tax year:** ").append(refund.taxYear()).append("\n")
                .append("**Last updated:** ").append(refund.lastUpdatedAt()).append("\n");
        }

        if (plan.includeEta()
            && refund.availableAtEstimated() != null
            && !RefundStatus.AVAILABLE.name().equals(refund.status())
            && !RefundStatus.REJECTED.name().equals(refund.status())) {
            sb.append("**Estimated availability:** ").append(refund.availableAtEstimated()).append("\n");
        }

        if (!plan.includeRefundStatus() && !plan.includeEta()) {
            sb.append("I can help with your refund workflow, but this plan did not expose refund status or ETA details.\n");
        }

        Confidence c = deriveConfidence(refund, plan);

        return new AssistantChatResponse(sb.toString(), citations, actions, c);
    }

    private static Confidence deriveConfidence(RefundStatusResponse refund, AssistantPlan plan) {
        if (plan.includeRefundStatus() && RefundStatus.AVAILABLE.name().equals(refund.status())) {
            return Confidence.HIGH;
        }
        if (plan.includeEta() && refund.availableAtEstimated() != null) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }

    private AssistantChatResponse parseStrict(String json) {
        try {
            return om.readValue(json, AssistantChatResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("LLM output failed schema/parse: " + e.getMessage());
        }
    }

    private static List<Action> buildActions(RefundStatusResponse refund) {
        List<Action> out = new ArrayList<>();
        out.add(new Action(ActionType.REFRESH, "Refresh status"));

        if (refund.trackingId() != null && !refund.trackingId().isBlank()) {
            out.add(new Action(ActionType.SHOW_TRACKING, "Show tracking details"));
        }

        if ("REJECTED".equals(refund.status())) {
            out.add(new Action(ActionType.CONTACT_SUPPORT, "Contact support"));
        } else if ("PROCESSING".equals(refund.status())) {
            out.add(new Action(ActionType.CONTACT_SUPPORT, "Contact support if no update in 21 days"));
        }
        return out;
    }

    private String safeJson(Object o) {
        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "assistant_response");
        schema.put("strict", true);
        schema.put("schema", Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                "answerMarkdown", Map.of("type", "string"),
                "citations", Map.of(
                    "type", "array",
                    "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                            "docId", Map.of("type", "string"),
                            "quote", Map.of("type", "string")
                        ),
                        "required", List.of("docId", "quote")
                    )
                ),
                "actions", Map.of(
                    "type", "array",
                    "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                            "type", Map.of("type", "string", "enum", List.of("REFRESH", "CONTACT_SUPPORT", "SHOW_TRACKING")),
                            "label", Map.of("type", "string")
                        ),
                        "required", List.of("type", "label")
                    )
                ),
                "confidence", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH"))
            ),
            "required", List.of("answerMarkdown", "citations", "actions", "confidence")
        ));
        return schema;
    }

    private static List<Action> mergeActions(List<Action> llmActions, List<Action> defaultActions) {
        List<Action> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Action a : llmActions) {
            if (a == null || a.type() == null) continue;
            String key = a.type().name() + "|" + (a.label() == null ? "" : a.label().trim());
            if (seen.add(key)) {
                out.add(a);
            }
        }

        for (Action a : defaultActions) {
            if (a == null || a.type() == null) continue;

            boolean sameTypeExists = out.stream().anyMatch(x -> x.type() == a.type());
            if (sameTypeExists) continue;

            String key = a.type().name() + "|" + (a.label() == null ? "" : a.label().trim());
            if (seen.add(key)) {
                out.add(a);
            }
        }

        return out;
    }
}