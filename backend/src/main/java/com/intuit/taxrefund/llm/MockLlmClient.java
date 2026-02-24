package com.intuit.taxrefund.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.Action;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.Confidence;
import com.intuit.taxrefund.refund.controller.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.model.RefundStatus;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MockLlmClient implements LlmClient {

    private static final String MODEL = "mock-assistant-v1";

    private final ObjectMapper om;

    public MockLlmClient(ObjectMapper om) {
        this.om = om;
    }

    @Override public String provider() { return "mock"; }
    @Override public String model() { return MODEL; }
    @Override public boolean isAvailable() { return true; }

    @Override
    public String generateStructuredJson(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        // We ignore prompts/schema here and generate a valid AssistantChatResponse JSON.
        // The userPrompt includes authoritativeData; we can optionally parse it, but simplest is to keep this deterministic.

        // We *try* to extract a few fields from userPrompt if present; otherwise output a generic answer.
        // Safer: just output a valid schema response with placeholders.
        String answer = """
**I’m in demo mode (mock AI).**
I can answer using the latest refund status shown in the app.

If you enabled a real provider (OpenAI), I’ll generate a richer answer with the same strict JSON schema.
""";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answerMarkdown", answer);
        out.put("citations", List.of());
        out.put("actions", List.of(
            Map.of("type", "REFRESH", "label", "Refresh status")
        ));
        out.put("confidence", "LOW");

        try {
            return om.writeValueAsString(out);
        } catch (Exception e) {
            // Last resort: hand-built JSON
            return "{\"answerMarkdown\":\"Mock mode.\",\"citations\":[],\"actions\":[{\"type\":\"REFRESH\",\"label\":\"Refresh status\"}],\"confidence\":\"LOW\"}";
        }
    }

    /**
     * Optional helper you can call from AssistantService if you prefer to generate
     * mock answers using real refund data (close to your old mockAnswer()).
     */
    public AssistantChatResponse buildMockAssistantResponse(
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
}