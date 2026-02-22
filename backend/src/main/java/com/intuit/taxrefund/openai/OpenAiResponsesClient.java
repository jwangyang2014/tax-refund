package com.intuit.taxrefund.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public String generateStructuredJson(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        if (!isEnabled()) throw new IllegalStateException("OpenAI API key not configured");

        Map<String, Object> body = Map.of(
            "model", props.model(),
            "input", new Object[] {
                Map.of("role", "developer", "content", developerPrompt),
                Map.of("role", "user", "content", userPrompt)
            },
            "text", Map.of(
                "format", Map.of(
                    "type", "json_schema",
                    "json_schema", jsonSchema
                )
            )
        );

        String raw;
        try {
            raw = rest.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + props.apiKey())
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            log.error("openai_http_failed model={} err={}", props.model(), e.toString());
            throw e;
        }

        try {
            JsonNode root = om.readTree(raw);
            JsonNode textNode = root.at("/output/0/content/0/text");
            if (textNode.isMissingNode()) {
                log.error("openai_unexpected_shape model={} rawSize={}", props.model(), raw == null ? 0 : raw.length());
                throw new IllegalStateException("Unexpected OpenAI response shape");
            }
            return textNode.asText();
        } catch (Exception e) {
            log.error("openai_parse_failed model={} err={}", props.model(), e.toString());
            throw new IllegalStateException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }
}