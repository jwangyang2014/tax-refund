package com.intuit.taxrefund.shared.outbox.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.refund.integration.eta.RefundEtaPrediction;
import com.intuit.taxrefund.refund.integration.eta.RefundEtaPredictionRepository;
import com.intuit.taxrefund.ml.MlEtaClient;
import com.intuit.taxrefund.shared.outbox.model.OutboxEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class OutboxEventHandler {

    private static final long SECONDS_IN_DAY = 86400L;

    private final RefundEtaPredictionRepository etaRepo;
    private final MlEtaClient ml;
    private final ObjectMapper om;

    public OutboxEventHandler(RefundEtaPredictionRepository etaRepo, MlEtaClient ml, ObjectMapper om) {
        this.etaRepo = etaRepo;
        this.ml = ml;
        this.om = om;
    }

    public void handle(OutboxEvent evt) throws Exception {
        if (!"REFUND_STATUS_UPDATED".equals(evt.getEventType())) {
            return;
        }

        JsonNode payload = om.readTree(evt.getPayload());

        Long userId = payload.path("userId").asLong();
        int taxYear = payload.path("taxYear").asInt();
        String status = payload.path("status").asText();

        BigDecimal expectedAmount = null;
        JsonNode amt = payload.get("expectedAmount");
        if (amt != null && !amt.isNull()) {
            expectedAmount = new BigDecimal(amt.asText());
        }

        String filingState = payload.path("filingState").asText("NA");

        MlEtaClient.PredictResponse pred = ml.predict(userId, taxYear, status, filingState, expectedAmount);

        if (etaRepo.existsByUserIdAndTaxYearAndStatusAndModelVersion(userId, taxYear, status, pred.modelVersion())) {
            // idempotency: already have this model's prediction for this status
            return;
        }

        Instant estimatedAvailableAt = Instant.now().plusSeconds((long) pred.etaDays() * SECONDS_IN_DAY);

        RefundEtaPrediction row = new RefundEtaPrediction(
            userId,
            taxYear,
            status,
            pred.etaDays(),
            estimatedAvailableAt,
            pred.modelName(),
            pred.modelVersion(),
            pred.featuresJson()
        );

        etaRepo.save(row);
    }
}