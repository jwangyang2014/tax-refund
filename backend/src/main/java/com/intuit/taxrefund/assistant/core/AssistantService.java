package com.intuit.taxrefund.assistant.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.assistant.api.dto.AssistantChatResponse;
import com.intuit.taxrefund.assistant.api.dto.AssistantChatResponse.Action;
import com.intuit.taxrefund.assistant.api.dto.AssistantChatResponse.ActionType;
import com.intuit.taxrefund.assistant.api.dto.AssistantChatResponse.Confidence;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.openai.OpenAiResponsesClient;
import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.service.RefundService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class AssistantService {

    private static final Logger log = LogManager.getLogger(AssistantService.class);

    private final RefundService refundService;
    private final IntentClassifier classifier;
    private final AssistantPlanner planner;
    private final ConversationStateStore stateStore;
    private final PolicySnippets policySnippets;
    private final OpenAiResponsesClient openai;
    private final ObjectMapper om;

    public AssistantService(
        RefundService refundService,
        IntentClassifier classifier,
        AssistantPlanner planner,
        ConversationStateStore stateStore,
        PolicySnippets policySnippets,
        OpenAiResponsesClient openai,
        ObjectMapper om
    ) {
        this.refundService = refundService;
        this.classifier = classifier;
        this.planner = planner;
        this.stateStore = stateStore;
        this.policySnippets = policySnippets;
        this.openai = openai;
        this.om = om;
    }

    public AssistantChatResponse answer(JwtService.JwtPrincipal principal, String question) {
        long userId = principal.userId();

        ConversationState prev = stateStore.get(userId);
        IntentClassifier.IntentResult r = classifier.classify(question);
        AssistantIntent intent = r.intent();

        log.info("assistant_intent userId={} intent={} confidence={} model={}",
            userId, r.intent(), r.confidence(), r.model());

        RefundStatusResponse refund = refundService.getLatestRefundStatus(principal);
        AssistantPlan plan = planner.plan(prev, intent, refund.status());

        log.info("assistant_plan userId={} intent={} refundStatus={} nextState={}",
            userId, intent, refund.status(), plan.nextState());

        var citations = plan.includePolicySnippets()
            ? policySnippets.forStatus(refund.status())
            : List.<AssistantChatResponse.Citation>of();

        var actions = buildActions(refund);

        stateStore.set(userId, plan.nextState());

        if (!openai.isEnabled()) {
            log.debug("assistant_openai_disabled userId={}", userId);
            return mockAnswer(question, refund, citations, actions);
        }

        Map<String, Object> authoritativeData = new LinkedHashMap<>();
        authoritativeData.put("refund", Map.of(
            "taxYear", refund.taxYear(),
            "status", refund.status(),
            "lastUpdatedAt", refund.lastUpdatedAt(),
            "expectedAmount", refund.expectedAmount(),
            "trackingId", refund.trackingId()
        ));
        authoritativeData.put("eta", Map.of(
            "estimatedAvailableAt", refund.availableAtEstimated()
        ));
        authoritativeData.put("policies", citations);

        Map<String, Object> schema = responseSchema();

        String developerPrompt = """
You are a TurboTax-like assistant. You must obey:
- Only use numeric/date facts from authoritativeData. Do NOT invent numbers or dates.
- If a needed fact is missing, say it's unknown.
- Return ONLY JSON that matches the provided schema.
""";

        String userPrompt = """
Question: %s

authoritativeData:
%s
""".formatted(question, safeJson(authoritativeData));

        String json;
        try {
            json = openai.generateStructuredJson(developerPrompt, userPrompt, schema);
        } catch (Exception e) {
            log.error("assistant_openai_call_failed userId={} err={}", userId, e.toString());
            return mockAnswer(question, refund, citations, actions);
        }

        AssistantChatResponse parsed;
        try {
            parsed = parseStrict(json);
        } catch (Exception e) {
            log.warn("assistant_openai_bad_output userId={} err={}", userId, e.toString());
            return mockAnswer(question, refund, citations, actions);
        }

        if (parsed.answerMarkdown() == null || parsed.answerMarkdown().isBlank()) {
            log.warn("assistant_openai_empty_answer userId={}", userId);
            return mockAnswer(question, refund, citations, actions);
        }

        return parsed;
    }

    private AssistantChatResponse parseStrict(String json) {
        try {
            return om.readValue(json, AssistantChatResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("LLM output failed schema/parse: " + e.getMessage());
        }
    }

    private AssistantChatResponse mockAnswer(
        String question,
        RefundStatusResponse refund,
        List<AssistantChatResponse.Citation> citations,
        List<Action> actions
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Your question:** ").append(question).append("\n\n")
            .append("**Latest refund status:** ").append(refund.status()).append("\n")
            .append("**Tax year:** ").append(refund.taxYear()).append("\n")
            .append("**Last updated:** ").append(refund.lastUpdatedAt()).append("\n");

        if (refund.availableAtEstimated() != null && !RefundStatus.AVAILABLE.name().equals(refund.status())) {
            sb.append("**Estimated availability:** ").append(refund.availableAtEstimated()).append("\n");
        }

        Confidence c = refund.availableAtEstimated() != null ? Confidence.MEDIUM : Confidence.LOW;
        if (RefundStatus.AVAILABLE.name().equals(refund.status())) c = Confidence.HIGH;

        return new AssistantChatResponse(sb.toString(), citations, actions, c);
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
        try { return om.writerWithDefaultPrettyPrinter().writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
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
}