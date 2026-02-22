package com.intuit.taxrefund.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiResponsesClient {

    private static final Logger log = LogManager.getLogger(OpenAiResponsesClient.class);

    private final OpenAiProps props;
    private final ObjectMapper om;
    private final RestClient rest;

    public OpenAiResponsesClient(OpenAiProps props, ObjectMapper om) {
        this.props = props;
        this.om = om;

        this.rest = RestClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

        log.info("openai_client_initialized enabled={} model={}", isEnabled(), props.model());
    }

    public boolean isEnabled() {
        return props.apiKey() != null && !props.apiKey().isBlank();
    }

    /**
     * Calls the Responses API with Structured Outputs (JSON schema) and returns the model's JSON text.
     *
     * jsonSchema parameter is expected to look like AssistantService.responseSchema():
     * {
     *   "name": "assistant_response",
     *   "strict": true,
     *   "schema": { ...JSON Schema object... }
     * }
     */
    public String generateStructuredJson(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        if (!isEnabled()) throw new IllegalStateException("OpenAI API key not configured");

        Map<String, Object> body = buildRequestBody(developerPrompt, userPrompt, jsonSchema);

        final String raw;
        try {
            raw = rest.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + props.apiKey())
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Log the OpenAI error payload; it often contains the exact missing/invalid param.
            String responseBody = safeBody(e.getResponseBodyAsString());
            log.error("openai_http_failed model={} status={} body={}",
                props.model(), e.getStatusCode().value(), responseBody);
            throw e;
        } catch (ResourceAccessException e) {
            // Typically timeouts / DNS / connection issues
            log.error("openai_http_failed model={} err={}", props.model(), e.toString());
            throw e;
        } catch (Exception e) {
            log.error("openai_http_failed model={} err={}", props.model(), e.toString());
            throw e;
        }

        return extractOutputText(raw);
    }

    private Map<String, Object> buildRequestBody(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        // Structured Outputs expects:
        // text: { format: { type:"json_schema", name:"...", strict:true, schema:{...} } }
        // name is REQUIRED. :contentReference[oaicite:2]{index=2}

        String name = String.valueOf(jsonSchema.getOrDefault("name", "assistant_response"));
        boolean strict = Boolean.TRUE.equals(jsonSchema.getOrDefault("strict", Boolean.TRUE));

        @SuppressWarnings("unchecked")
        Map<String, Object> schemaOnly = (Map<String, Object>) jsonSchema.get("schema");
        if (schemaOnly == null) {
            throw new IllegalArgumentException("jsonSchema must contain key 'schema' with a JSON Schema object");
        }

        Map<String, Object> format = Map.of(
            "type", "json_schema",
            "name", name,
            "strict", strict,
            "schema", schemaOnly
        );

        // Use explicit content items (type: input_text) to align with Responses API structure.
        // This avoids relying on older "string content" shapes.
        Map<String, Object> developerMsg = Map.of(
            "role", "developer",
            "content", List.of(Map.of("type", "input_text", "text", developerPrompt))
        );

        Map<String, Object> userMsg = Map.of(
            "role", "user",
            "content", List.of(Map.of("type", "input_text", "text", userPrompt))
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("input", List.of(developerMsg, userMsg));
        body.put("text", Map.of("format", format));

        // For demos with sensitive-ish data, it's reasonable to disable storage by default.
        // (We can remove this if we want server-side storage.)
        body.put("store", false);

        // cap output length to avoid runaway responses
        // body.put("max_output_tokens", 800);
        return body;
    }

    private String extractOutputText(String raw) {
        try {
            JsonNode root = om.readTree(raw);

            // The docs warn the output array can include multiple items and
            // it's not safe to assume output[0].content[0].text. :contentReference[oaicite:3]{index=3}
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode outItem : output) {
                    JsonNode content = outItem.path("content");
                    if (!content.isArray()) continue;

                    for (JsonNode c : content) {
                        // Look for text-bearing items
                        // Common shape: { "type": "output_text", "text": "..." }
                        JsonNode text = c.get("text");
                        if (text != null && text.isTextual()) {
                            return text.asText();
                        }
                    }
                }
            }

            // If we didn't find any text, log shape and fail clearly.
            log.error("openai_unexpected_shape model={} rawSize={} rawPrefix={}",
                props.model(),
                raw == null ? 0 : raw.length(),
                raw == null ? "null" : raw.substring(0, Math.min(raw.length(), 500))
            );
            throw new IllegalStateException("Unexpected OpenAI response shape: no text content found");
        } catch (Exception e) {
            log.error("openai_parse_failed model={} err={}", props.model(), e.toString());
            throw new IllegalStateException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private static String safeBody(String s) {
        if (s == null) return "";
        // Avoid logging huge payloads
        return s.length() <= 2000 ? s : s.substring(0, 2000) + "...(truncated)";
    }
}