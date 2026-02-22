package com.intuit.taxrefund.refund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.ai.model.RefundEtaPrediction;
import com.intuit.taxrefund.ai.repo.RefundEtaPredictionRepository;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.repo.UserRepository;
import com.intuit.taxrefund.outbox.model.OutboxEvent;
import com.intuit.taxrefund.outbox.repo.OutboxEventRepository;
import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.model.RefundAccessAudit;
import com.intuit.taxrefund.refund.model.RefundRecord;
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.model.RefundStatusEvent;
import com.intuit.taxrefund.refund.repo.RefundAccessAuditRepository;
import com.intuit.taxrefund.refund.repo.RefundRecordRepository;
import com.intuit.taxrefund.refund.repo.RefundStatusEventRepository;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class RefundService {

    private static final Logger log = LogManager.getLogger(RefundService.class);

    private final RefundRecordRepository refundRepo;
    private final UserRepository userRepo;
    private final IrsAdapter irs;

    private final RefundStatusEventRepository statusEventRepo;
    private final OutboxEventRepository outboxRepo;
    private final RefundEtaPredictionRepository etaRepo;

    private final RefundAccessAuditRepository auditRepo;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefundService(
        RefundRecordRepository refundRepo,
        UserRepository userRepo,
        IrsAdapter irs,
        RefundStatusEventRepository statusEventRepo,
        OutboxEventRepository outboxRepo,
        RefundEtaPredictionRepository etaRepo,
        RefundAccessAuditRepository auditRepo,
        StringRedisTemplate redis,
        ObjectMapper objectMapper
    ) {
        this.refundRepo = refundRepo;
        this.userRepo = userRepo;
        this.irs = irs;

        this.statusEventRepo = statusEventRepo;
        this.outboxRepo = outboxRepo;
        this.etaRepo = etaRepo;

        this.auditRepo = auditRepo;

        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RefundStatusResponse getLatestRefundStatus(JwtService.JwtPrincipal principal, String correlationId) {
        Long userId = principal.userId();
        String cacheKey = "refund:latest:" + userId;

        boolean success = false;

        try {
            // 0) Cache read
            String cached = null;
            try {
                cached = redis.opsForValue().get(cacheKey);
            } catch (Exception e) {
                log.warn("refund_latest_cache_read_failed userId={} err={}", userId, e.toString());
            }

            if (cached != null) {
                try {
                    RefundStatusResponse resp = objectMapper.readValue(cached, RefundStatusResponse.class);
                    log.debug("refund_latest_cache_hit userId={} taxYear={} status={}", userId, resp.taxYear(), resp.status());
                    success = true;
                    return resp;
                } catch (Exception e) {
                    log.warn("refund_latest_cache_corrupt userId={} err={}", userId, e.toString());
                    // ignore cache
                }
            } else {
                log.debug("refund_latest_cache_miss userId={}", userId);
            }

            // 1) Fetch latest from IRS adapter
            IrsAdapter.IrsRefundResult irsResult;
            try {
                irsResult = irs.fetchMostRecentRefund(userId);
            } catch (Exception e) {
                log.error("irs_fetch_failed userId={} err={}", userId, e.toString());
                throw e;
            }

            // 2) Load/create record
            RefundRecord record = refundRepo.findByUserIdAndTaxYear(userId, irsResult.taxYear())
                .orElseGet(() -> {
                    AppUser user = userRepo.findById(userId).orElseThrow();
                    log.info("refund_record_created userId={} taxYear={}", userId, irsResult.taxYear());
                    return new RefundRecord(user, irsResult.taxYear(), RefundStatus.RECEIVED);
                });

            // 3) Update record and write event + outbox if status changed
            RefundStatus oldStatus = record.getStatus();
            record.updateFromIrs(irsResult.status(), irsResult.expectedAmount(), irsResult.trackingId());

            RefundStatus newStatus = record.getStatus();
            boolean statusChanged = oldStatus != newStatus;

            if (statusChanged) {
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

                String payloadJson;
                try {
                    payloadJson = objectMapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "taxYear", record.getTaxYear(),
                        "filingState", record.getUser().getState(),
                        "status", newStatus.name(),
                        "expectedAmount", record.getExpectedAmount(),
                        "trackingId", record.getIrsTrackingId()
                    ));
                } catch (Exception e) {
                    payloadJson = "{\"userId\":" + userId + ",\"taxYear\":" + record.getTaxYear() + ",\"status\":\"" + newStatus.name() + "\"}";
                    log.warn("outbox_payload_fallback userId={} taxYear={} err={}", userId, record.getTaxYear(), e.toString());
                }

                outboxRepo.save(OutboxEvent.newEvent(
                    "REFUND_STATUS_UPDATED",
                    userId + ":" + record.getTaxYear(),
                    payloadJson
                ));

                // Invalidate cache on change
                try {
                    redis.delete(cacheKey);
                } catch (Exception e) {
                    log.warn("refund_latest_cache_invalidate_failed userId={} err={}", userId, e.toString());
                }
            }

            // 4) Read latest persisted ETA prediction (do NOT call AI inline)
            Instant estimatedAvailableAt = record.getAvailableAtEstimated(); // fallback
            RefundEtaPrediction pred = null;
            try {
                pred = etaRepo
                    .findTopByUserIdAndTaxYearAndStatusOrderByCreatedAtDesc(userId, record.getTaxYear(), record.getStatus().name())
                    .orElse(null);
            } catch (Exception e) {
                log.warn("eta_prediction_lookup_failed userId={} taxYear={} status={} err={}",
                    userId, record.getTaxYear(), record.getStatus(), e.toString());
            }

            if (pred != null && pred.getEstimatedAvailableAt() != null) {
                estimatedAvailableAt = pred.getEstimatedAvailableAt();
                record.setAvailableAtEstimated(estimatedAvailableAt);
                log.info("eta_prediction_applied userId={} taxYear={} status={} estimatedAvailableAt={}",
                    userId, record.getTaxYear(), record.getStatus(), estimatedAvailableAt);
            } else {
                log.debug("eta_prediction_missing userId={} taxYear={} status={}", userId, record.getTaxYear(), record.getStatus());
            }

            refundRepo.save(record);

            RefundStatusResponse resp = new RefundStatusResponse(
                record.getTaxYear(),
                record.getStatus().name(),
                record.getLastUpdatedAt(),
                record.getExpectedAmount(),
                record.getIrsTrackingId(),
                estimatedAvailableAt,
                null
            );

            // 5) Cache response (short TTL to handle burst traffic)
            try {
                redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(resp), Duration.ofSeconds(60));
                log.debug("refund_latest_cache_set userId={} ttlSec=60", userId);
            } catch (Exception e) {
                log.warn("refund_latest_cache_write_failed userId={} err={}", userId, e.toString());
            }

            success = true;
            return resp;

        } finally {
            // Security/Compliance: audit read access (metadata only, no payload)
            try {
                auditRepo.save(RefundAccessAudit.of(userId, "GET /api/refund/latest", success, correlationId));
            } catch (Exception e) {
                log.warn("refund_access_audit_write_failed userId={} err={}", userId, e.toString());
            }
        }
    }
}