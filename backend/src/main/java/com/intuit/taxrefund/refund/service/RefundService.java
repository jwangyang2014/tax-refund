package com.intuit.taxrefund.refund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.refund.controller.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.integration.irs.IrsAdapter;
import com.intuit.taxrefund.refund.model.RefundAccessAudit;
import com.intuit.taxrefund.refund.repository.RefundAccessAuditRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefundService {

    private static final Logger log = LogManager.getLogger(RefundService.class);

    private final IrsAdapter irs;
    private final RefundStatusPersistenceService persistenceService;
    private final RefundAccessAuditRepository auditRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefundService(
        IrsAdapter irs,
        RefundStatusPersistenceService persistenceService,
        RefundAccessAuditRepository auditRepo,
        StringRedisTemplate redis,
        ObjectMapper objectMapper
    ) {
        this.irs = irs;
        this.persistenceService = persistenceService;
        this.auditRepo = auditRepo;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public RefundStatusResponse getLatestRefundStatus(JwtService.JwtPrincipal principal, String correlationId) {
        Long userId = principal.userId();
        String cacheKey = cacheKey(userId);
        boolean success = false;

        try {
            // 0) Cache read
            RefundStatusResponse cached = tryReadCache(cacheKey, userId);
            if (cached != null) {
                success = true;
                return cached;
            }

            // 1) Fetch latest from IRS adapter (outside tx)
            IrsAdapter.IrsRefundResult irsResult = fetchIrs(userId);

            // 2) Persist/update in a narrow transaction
            RefundStatusPersistenceService.PersistedRefundView view =
                persistenceService.upsertLatestFromIrs(userId, irsResult, cacheKey);

            // 3) Build response
            RefundStatusResponse resp = new RefundStatusResponse(
                view.taxYear(),
                view.status(),
                view.lastUpdatedAt(),
                view.expectedAmount(),
                view.trackingId(),
                view.estimatedAvailableAt(),
                null
            );

            // 4) Cache response (outside tx)
            tryWriteCache(cacheKey, userId, resp);

            success = true;
            return resp;

        } finally {
            writeAudit(userId, correlationId, success);
        }
    }

    private String cacheKey(Long userId) {
        return "refund:latest:" + userId;
    }

    private RefundStatusResponse tryReadCache(String cacheKey, Long userId) {
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached == null) {
                log.debug("refund_latest_cache_miss userId={}", userId);
                return null;
            }

            RefundStatusResponse resp = objectMapper.readValue(cached, RefundStatusResponse.class);
            log.debug("refund_latest_cache_hit userId={} taxYear={} status={}", userId, resp.taxYear(), resp.status());
            return resp;

        } catch (Exception e) {
            log.warn("refund_latest_cache_read_or_parse_failed userId={} err={}", userId, e.toString());
            return null;
        }
    }

    private IrsAdapter.IrsRefundResult fetchIrs(Long userId) {
        try {
            return irs.fetchMostRecentRefund(userId);
        } catch (Exception e) {
            log.error("irs_fetch_failed userId={} err={}", userId, e.toString());
            throw e;
        }
    }

    private void tryWriteCache(String cacheKey, Long userId, RefundStatusResponse resp) {
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(resp), Duration.ofSeconds(60));
            log.debug("refund_latest_cache_set userId={} ttlSec=60", userId);
        } catch (Exception e) {
            log.warn("refund_latest_cache_write_failed userId={} err={}", userId, e.toString());
        }
    }

    private void writeAudit(Long userId, String correlationId, boolean success) {
        try {
            auditRepo.save(RefundAccessAudit.of(userId, "GET /api/refund/latest", success, correlationId));
        } catch (Exception e) {
            log.warn("refund_access_audit_write_failed userId={} err={}", userId, e.toString());
        }
    }
}