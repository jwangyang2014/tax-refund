package com.intuit.taxrefund.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.llm.AiProps;
import com.intuit.taxrefund.llm.LlmClient;
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
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LogManager.getLogger(OpenAiLlmClient.class);

    private final AiProps aiProps;
    private final ObjectMapper om;
    private final RestClient rest;

    public OpenAiLlmClient(AiProps aiProps, ObjectMapper om) {
        this.aiProps = aiProps;
        this.om = om;
        this.rest = RestClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();

        log.info("openai_llm_initialized enabled={} model={}", isAvailable(), model());
    }

    @Override public String provider() { return "openai"; }

    @Override
    public String model() {
        AiProps.OpenAi o = aiProps.openai();
        String m = (o == null) ? null : o.model();
        return (m == null || m.isBlank()) ? "gpt-4o-mini" : m;
    }

    @Override
    public boolean isAvailable() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    private String apiKey() {
        AiProps.OpenAi o = aiProps.openai();
        return (o == null) ? null : o.apiKey();
    }

    /**
     * Calls OpenAI Responses API with Structured Outputs (JSON Schema).
     * Returns the model's JSON text (not the full OpenAI payload).
     */
    @Override
    public String generateStructuredJson(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        if (!isAvailable()) throw new IllegalStateException("OpenAI API key not configured");

        Map<String, Object> body = buildRequestBody(developerPrompt, userPrompt, jsonSchema);

        final String raw;
        try {
            raw = rest.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey())
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("openai_http_failed model={} status={} body={}",
                model(), e.getStatusCode().value(), safeBody(e.getResponseBodyAsString()));
            throw e;
        } catch (ResourceAccessException e) {
            log.error("openai_http_failed model={} err={}", model(), e.toString());
            throw e;
        } catch (Exception e) {
            log.error("openai_http_failed model={} err={}", model(), e.toString());
            throw e;
        }

        return extractOutputText(raw);
    }

    private Map<String, Object> buildRequestBody(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
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

        Map<String, Object> developerMsg = Map.of(
            "role", "developer",
            "content", List.of(Map.of("type", "input_text", "text", developerPrompt))
        );

        Map<String, Object> userMsg = Map.of(
            "role", "user",
            "content", List.of(Map.of("type", "input_text", "text", userPrompt))
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", List.of(developerMsg, userMsg));
        body.put("text", Map.of("format", format));
        body.put("store", false);
        return body;
    }

    private String extractOutputText(String raw) {
        try {
            JsonNode root = om.readTree(raw);
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode outItem : output) {
                    JsonNode content = outItem.path("content");
                    if (!content.isArray()) continue;
                    for (JsonNode c : content) {
                        JsonNode text = c.get("text");
                        if (text != null && text.isTextual()) {
                            return text.asText();
                        }
                    }
                }
            }

            log.error("openai_unexpected_shape model={} rawSize={} rawPrefix={}",
                model(),
                raw == null ? 0 : raw.length(),
                raw == null ? "null" : raw.substring(0, Math.min(raw.length(), 500))
            );
            throw new IllegalStateException("Unexpected OpenAI response shape: no text content found");
        } catch (Exception e) {
            log.error("openai_parse_failed model={} err={}", model(), e.toString());
            throw new IllegalStateException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private static String safeBody(String s) {
        if (s == null) return "";
        return s.length() <= 2000 ? s : s.substring(0, 2000) + "...(truncated)";
    }
}