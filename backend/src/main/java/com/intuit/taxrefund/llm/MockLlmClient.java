package com.intuit.taxrefund.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockLlmClient implements LlmClient {

    private static final String MODEL = "mock-assistant-v1";

    private final ObjectMapper om;

    public MockLlmClient(ObjectMapper om) {
        this.om = om;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return MODEL;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String generateStructuredJson(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        String answer = buildAnswerFromPrompt(userPrompt);

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
            return """
                {
                  "answerMarkdown":"**I’m in demo mode (mock AI).**\\n\\nPlease refresh your refund status for the latest information.",
                  "citations":[],
                  "actions":[{"type":"REFRESH","label":"Refresh status"}],
                  "confidence":"LOW"
                }
                """;
        }
    }

    private String buildAnswerFromPrompt(String userPrompt) {
        String question = extractQuestion(userPrompt);
        String taxYear = extractJsonField(userPrompt, "taxYear");
        String status = extractJsonField(userPrompt, "status");
        String estimatedAvailableAt = extractJsonField(userPrompt, "estimatedAvailableAt");

        StringBuilder sb = new StringBuilder();
        sb.append("**I’m in demo mode (mock AI).**\n\n");

        if (question != null && !question.isBlank()) {
            sb.append("**Your question:** ").append(question).append("\n\n");
        }

        if (status != null && !status.isBlank()) {
            sb.append("**Latest refund status:** ").append(status).append("\n");
        } else {
            sb.append("**Latest refund status:** Unknown\n");
        }

        if (taxYear != null && !taxYear.isBlank()) {
            sb.append("**Tax year:** ").append(taxYear).append("\n");
        }

        if (estimatedAvailableAt != null && !estimatedAvailableAt.isBlank() && !"null".equalsIgnoreCase(estimatedAvailableAt)) {
            sb.append("**Estimated availability:** ").append(estimatedAvailableAt).append("\n");
        }

        sb.append("\n");
        sb.append("This response is generated locally by the mock provider and follows the same JSON contract as the real LLM integration.");

        return sb.toString();
    }

    private String extractQuestion(String userPrompt) {
        if (userPrompt == null) return null;

        String marker = "Question:";
        int start = userPrompt.indexOf(marker);
        if (start < 0) return null;

        int contentStart = start + marker.length();
        int end = userPrompt.indexOf("authoritativeData:", contentStart);
        if (end < 0) end = userPrompt.length();

        return userPrompt.substring(contentStart, end).trim();
    }

    private String extractJsonField(String userPrompt, String fieldName) {
        if (userPrompt == null) return null;

        int start = userPrompt.indexOf("authoritativeData:");
        if (start < 0) return null;

        String jsonPart = userPrompt.substring(start + "authoritativeData:".length()).trim();

        try {
            JsonNode root = om.readTree(jsonPart);
            JsonNode value = findFieldRecursive(root, fieldName);
            if (value == null || value.isMissingNode() || value.isNull()) return null;
            return value.isTextual() ? value.asText() : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode findFieldRecursive(JsonNode node, String fieldName) {
        if (node == null) return null;

        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null) return direct;

            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode found = findFieldRecursive(entry.getValue(), fieldName);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findFieldRecursive(item, fieldName);
                if (found != null) return found;
            }
        }

        return null;
    }
}