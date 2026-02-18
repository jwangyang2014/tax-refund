package com.intuit.taxrefundstatus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefundstatus.ai.AiConfig;
import com.intuit.taxrefundstatus.refund.model.RefundStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class OpenAiClient implements AiClient {
    private final AiConfig config;
    private final RestClient restClient;
    private final ObjectMapper om = new ObjectMapper();

    public OpenAiClient(AiConfig config) {
        this.config = config;
        this.restClient = RestClient.builder().build(); // Placeholder, use WebClient or RestTemplate in real code
    }

    @Override
    public String getModel() {
        return config.getOpenAi().getModel();
    }

    @Override
    public PredictEtaResult predictRefundEtaDays(RefundStatus status, BigDecimal expectedAmount) {
        AiConfig.OpenAi openAiConfig = config.getOpenAi();
        String apiKey = openAiConfig.getApiKey();
        String model = openAiConfig.getModel();
        if (apiKey == null || apiKey.isBlank()) {
            return new PredictEtaResult(-1, "OpenAI API key not configured", "openai", openAiConfig.getModel());
        }

        String prompt = """
            You are an assistant predicting when a US tax refund becomes AVAILABLE after filling.
            Input:
            - status: %s
            - refund_amount: $%s
            Return JSON: {"etaDays": <int 0...365>, "explanation": "<short>"} only.
            """.formatted(status.name(), expectedAmount);

        Map<String, Object> body = Map.of(
            "model", model,
            "messages", new Object[] {
                Map.of("role", "system", "content", "Return Only JSON."),
                Map.of("role", "user", "content", prompt)
            },
            "temperature", 0.2
        );

        try {
            String raw = restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            // Simplified parsing, real response has more nesting
            JsonNode tree = om.readTree(raw);
            String content = tree.at("/choices/0/message/content").asText();
            JsonNode json = om.readTree(content);
            int etaDays = json.get("etaDays").asInt();
            String explanation = json.get("explanation").asText();
            return new PredictEtaResult(etaDays, explanation, "openai", model);
        } catch (Exception e) {
            return new PredictEtaResult(-1, "OpenAI API error: " + e.getMessage(), "openai", model);
        }
    }
}
