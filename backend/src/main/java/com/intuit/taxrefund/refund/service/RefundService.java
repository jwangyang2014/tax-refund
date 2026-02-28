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
    private static final Duration REFUND_CACHE_TTL = Duration.ofSeconds(60);

    private final IrsAdapter irsAdapter;
    private final RefundSyncService refundSyncService;
    private final RefundAccessAuditRepository auditRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefundService(
        IrsAdapter irsAdapter,
        RefundSyncService refundSyncService,
        RefundAccessAuditRepository auditRepo,
        StringRedisTemplate redis,
        ObjectMapper objectMapper
    ) {
        this.irsAdapter = irsAdapter;
        this.refundSyncService = refundSyncService;
        this.auditRepo = auditRepo;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * API use case: return latest refund status for the authenticated user.
     * Note: this performs on-demand synchronization from IRS on cache miss.
     */
    public RefundStatusResponse getLatestRefundStatus(JwtService.JwtPrincipal principal, String correlationId) {
        Long userId = principal.userId();
        String cacheKey = latestRefundCacheKey(userId);
        boolean success = false;

        try {
            RefundStatusResponse cached = tryReadLatestRefundFromCache(cacheKey, userId);
            if (cached != null) {
                success = true;
                return cached;
            }

            RefundStatusResponse response = refreshLatestRefundForUser(userId);
            tryWriteLatestRefundToCache(cacheKey, userId, response);

            success = true;
            return response;
        } finally {
            writeAccessAudit(userId, correlationId, success);
        }
    }

    /**
     * Reusable orchestration: fetch latest IRS status and reconcile it into local DB.
     * Future scheduler / polling job can reuse this directly.
     */
    private RefundStatusResponse refreshLatestRefundForUser(Long userId) {
        IrsAdapter.IrsRefundResult irsResult = fetchLatestFromIrs(userId);

        RefundSyncService.ReconciledRefundView reconciled =
            refundSyncService.reconcileLatestRefundFromIrs(userId, irsResult);

        return new RefundStatusResponse(
            reconciled.taxYear(),
            reconciled.status(),
            reconciled.lastUpdatedAt(),
            reconciled.expectedAmount(),
            reconciled.trackingId(),
            reconciled.estimatedAvailableAt(),
            null
        );
    }

    private IrsAdapter.IrsRefundResult fetchLatestFromIrs(Long userId) {
        try {
            return irsAdapter.fetchMostRecentRefund(userId);
        } catch (Exception e) {
            log.error("irs_fetch_failed userId={} err={}", userId, e.toString());
            throw e;
        }
    }

    private String latestRefundCacheKey(Long userId) {
        return "refund:latest:" + userId;
    }

    private RefundStatusResponse tryReadLatestRefundFromCache(String cacheKey, Long userId) {
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

    private void tryWriteLatestRefundToCache(String cacheKey, Long userId, RefundStatusResponse response) {
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), REFUND_CACHE_TTL);
            log.debug("refund_latest_cache_set userId={} ttlSec={}", userId, REFUND_CACHE_TTL.toSeconds());
        } catch (Exception e) {
            log.warn("refund_latest_cache_write_failed userId={} err={}", userId, e.toString());
        }
    }

    private void writeAccessAudit(Long userId, String correlationId, boolean success) {
        try {
            auditRepo.save(RefundAccessAudit.of(userId, "GET /api/refund/latest", success, correlationId));
        } catch (Exception e) {
            log.warn("refund_access_audit_write_failed userId={} err={}", userId, e.toString());
        }
    }
}