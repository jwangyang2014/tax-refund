package com.intuit.taxrefund.refund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.repository.UserRepository;
import com.intuit.taxrefund.refund.integration.eta.RefundEtaPrediction;
import com.intuit.taxrefund.refund.integration.eta.RefundEtaPredictionRepository;
import com.intuit.taxrefund.refund.integration.irs.IrsAdapter;
import com.intuit.taxrefund.refund.model.RefundRecord;
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.model.RefundStatusEvent;
import com.intuit.taxrefund.refund.repository.RefundRecordRepository;
import com.intuit.taxrefund.refund.repository.RefundStatusEventRepository;
import com.intuit.taxrefund.shared.outbox.model.OutboxEvent;
import com.intuit.taxrefund.shared.outbox.repo.OutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class RefundStatusPersistenceService {

    private static final Logger log = LogManager.getLogger(RefundStatusPersistenceService.class);

    private final RefundRecordRepository refundRepo;
    private final UserRepository userRepo;
    private final RefundStatusEventRepository statusEventRepo;
    private final OutboxEventRepository outboxRepo;
    private final RefundEtaPredictionRepository etaRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefundStatusPersistenceService(
        RefundRecordRepository refundRepo,
        UserRepository userRepo,
        RefundStatusEventRepository statusEventRepo,
        OutboxEventRepository outboxRepo,
        RefundEtaPredictionRepository etaRepo,
        StringRedisTemplate redis,
        ObjectMapper objectMapper
    ) {
        this.refundRepo = refundRepo;
        this.userRepo = userRepo;
        this.statusEventRepo = statusEventRepo;
        this.outboxRepo = outboxRepo;
        this.etaRepo = etaRepo;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistedRefundView upsertLatestFromIrs(Long userId, IrsAdapter.IrsRefundResult irsResult, String cacheKey) {
        RefundRecord record = loadOrCreateRecord(userId, irsResult);

        RefundStatus oldStatus = record.getStatus();
        record.updateFromIrs(irsResult.status(), irsResult.expectedAmount(), irsResult.trackingId());

        RefundStatus newStatus = record.getStatus();
        boolean statusChanged = oldStatus != newStatus;

        if (statusChanged) {
            onStatusChanged(userId, record, oldStatus, newStatus);
            invalidateCache(cacheKey, userId); // best-effort; okay if it fails
        }

        Instant estimatedAvailableAt = applyLatestEtaPrediction(userId, record);

        refundRepo.save(record);

        return new PersistedRefundView(
            record.getTaxYear(),
            record.getStatus().name(),
            record.getLastUpdatedAt(),
            record.getExpectedAmount(),
            record.getIrsTrackingId(),
            estimatedAvailableAt
        );
    }

    private RefundRecord loadOrCreateRecord(Long userId, IrsAdapter.IrsRefundResult irsResult) {
        return refundRepo.findByUserIdAndTaxYear(userId, irsResult.taxYear())
            .orElseGet(() -> {
                AppUser user = userRepo.findById(userId).orElseThrow();
                log.info("refund_record_created userId={} taxYear={}", userId, irsResult.taxYear());
                return new RefundRecord(user, irsResult.taxYear(), RefundStatus.RECEIVED);
            });
    }

    private void onStatusChanged(Long userId, RefundRecord record, RefundStatus oldStatus, RefundStatus newStatus) {
        log.info("refund_status_changed userId={} taxYear={} oldStatus={} newStatus={}",
            userId, record.getTaxYear(), oldStatus, newStatus);

        statusEventRepo.save(RefundStatusEvent.of(
            userId,
            record.getTaxYear(),
            record.getUser().getState(),
            oldStatus,
            newStatus,
            record.getExpectedAmount(),
            record.getIrsTrackingId(),
            "IRS"
        ));

        outboxRepo.save(OutboxEvent.newEvent(
            "REFUND_STATUS_UPDATED",
            userId + ":" + record.getTaxYear(),
            buildOutboxPayload(userId, record, newStatus)
        ));
    }

    private String buildOutboxPayload(Long userId, RefundRecord record, RefundStatus newStatus) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "userId", userId,
                "taxYear", record.getTaxYear(),
                "filingState", record.getUser().getState(),
                "status", newStatus.name(),
                "expectedAmount", record.getExpectedAmount(),
                "trackingId", record.getIrsTrackingId()
            ));
        } catch (Exception e) {
            log.warn("outbox_payload_fallback userId={} taxYear={} err={}", userId, record.getTaxYear(), e.toString());
            return "{\"userId\":" + userId + ",\"taxYear\":" + record.getTaxYear() + ",\"status\":\"" + newStatus.name() + "\"}";
        }
    }

    private void invalidateCache(String cacheKey, Long userId) {
        try {
            redis.delete(cacheKey);
        } catch (Exception e) {
            log.warn("refund_latest_cache_invalidate_failed userId={} err={}", userId, e.toString());
        }
    }

    private Instant applyLatestEtaPrediction(Long userId, RefundRecord record) {
        Instant estimatedAvailableAt = record.getAvailableAtEstimated(); // fallback

        try {
            RefundEtaPrediction pred = etaRepo
                .findTopByUserIdAndTaxYearAndStatusOrderByCreatedAtDesc(
                    userId,
                    record.getTaxYear(),
                    record.getStatus().name()
                )
                .orElse(null);

            if (pred != null && pred.getEstimatedAvailableAt() != null) {
                estimatedAvailableAt = pred.getEstimatedAvailableAt();
                record.setAvailableAtEstimated(estimatedAvailableAt);

                log.info("eta_prediction_applied userId={} taxYear={} status={} estimatedAvailableAt={}",
                    userId, record.getTaxYear(), record.getStatus(), estimatedAvailableAt);
            } else {
                log.debug("eta_prediction_missing userId={} taxYear={} status={}",
                    userId, record.getTaxYear(), record.getStatus());
            }
        } catch (Exception e) {
            log.warn("eta_prediction_lookup_failed userId={} taxYear={} status={} err={}",
                userId, record.getTaxYear(), record.getStatus(), e.toString());
        }

        return estimatedAvailableAt;
    }

    public record PersistedRefundView(
        Integer taxYear,
        String status,
        Instant lastUpdatedAt,
        BigDecimal expectedAmount,
        String trackingId,
        Instant estimatedAvailableAt
    ) {}
}