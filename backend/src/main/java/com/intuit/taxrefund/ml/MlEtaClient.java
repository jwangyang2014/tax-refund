package com.intuit.taxrefund.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class MlEtaClient {

    private static final Logger log = LogManager.getLogger(MlEtaClient.class);

    private final RestClient rest;
    private final ObjectMapper om;

    public MlEtaClient(MlProps props, ObjectMapper om) {
        this.rest = RestClient.builder()
            .baseUrl(props.baseUrl())
            .build();
        this.om = om;

        log.info("ml_client_initialized baseUrl={}", props.baseUrl());
    }

    public ModelInfo modelInfo() {
        try {
            String raw = rest.get()
                .uri("/model/info")
                .retrieve()
                .body(String.class);

            JsonNode n = om.readTree(raw);
            ModelInfo info = new ModelInfo(
                n.path("modelName").asText("unknown"),
                n.path("modelVersion").asText("unknown")
            );
            log.info("ml_model_info modelName={} modelVersion={}", info.modelName(), info.modelVersion());
            return info;
        } catch (Exception e) {
            log.warn("ml_model_info_failed err={}", e.toString());
            return new ModelInfo("unknown", "unavailable");
        }
    }

    public PredictResponse predict(Long userId, int taxYear, String status, String filingState, BigDecimal expectedAmount) {
        Map<String, Object> body = Map.of(
            "userId", userId,
            "taxYear", taxYear,
            "status", status,
            "filingState", filingState,
            "expectedAmount", expectedAmount
        );

        String raw;
        try {
            raw = rest.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            log.error("ml_predict_http_failed userId={} taxYear={} status={} err={}", userId, taxYear, status, e.toString());
            throw e;
        }

        try {
            JsonNode n = om.readTree(raw);
            int etaDays = n.path("etaDays").asInt();
            String modelName = n.path("modelName").asText("unknown");
            String modelVersion = n.path("modelVersion").asText("unknown");
            JsonNode features = n.path("features");

            String featuresJson = features.isMissingNode() ? "{}" : om.writeValueAsString(features);

            log.info("ml_predict_ok userId={} taxYear={} status={} etaDays={} modelName={} modelVersion={}",
                userId, taxYear, status, etaDays, modelName, modelVersion);

            return new PredictResponse(etaDays, modelName, modelVersion, featuresJson);
        } catch (Exception e) {
            log.error("ml_predict_parse_failed userId={} taxYear={} status={} err={}", userId, taxYear, status, e.toString());
            throw new IllegalStateException("Failed to parse ML response: " + e.getMessage(), e);
        }
    }

    public record PredictResponse(int etaDays, String modelName, String modelVersion, String featuresJson) {}
    public record ModelInfo(String modelName, String modelVersion) {}
}