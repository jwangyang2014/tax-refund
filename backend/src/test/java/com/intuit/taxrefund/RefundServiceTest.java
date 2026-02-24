package com.intuit.taxrefund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.refund.controller.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.integration.irs.IrsAdapter;
import com.intuit.taxrefund.refund.model.RefundAccessAudit;
import com.intuit.taxrefund.refund.repository.RefundAccessAuditRepository;
import com.intuit.taxrefund.refund.service.RefundService;
import com.intuit.taxrefund.refund.service.RefundStatusPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {

  private static RefundService newSvc(
      IrsAdapter irs,
      RefundStatusPersistenceService persistenceService,
      RefundAccessAuditRepository auditRepo,
      StringRedisTemplate redis,
      ObjectMapper objectMapper
  ) {
    return new RefundService(
        irs,
        persistenceService,
        auditRepo,
        redis,
        objectMapper
    );
  }

  @Test
  void latest_whenCacheMiss_fetchesIrs_callsPersistence_cachesResponse_andAuditsSuccess() throws Exception {
    IrsAdapter irs = mock(IrsAdapter.class);
    RefundStatusPersistenceService persistenceService = mock(RefundStatusPersistenceService.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null); // cache miss

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RefundService svc = newSvc(irs, persistenceService, auditRepo, redis, objectMapper);

    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, com.intuit.taxrefund.refund.model.RefundStatus.PROCESSING, new BigDecimal("999.99"), "IRS-1"
    ));

    Instant now = Instant.now();
    Instant predictedAt = now.plusSeconds(7 * 86400L);

    when(persistenceService.upsertLatestFromIrs(eq(1L), any(IrsAdapter.IrsRefundResult.class), eq("refund:latest:1")))
        .thenReturn(new RefundStatusPersistenceService.PersistedRefundView(
            2025,
            "PROCESSING",
            now,
            new BigDecimal("999.99"),
            "IRS-1",
            predictedAt
        ));

    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal, "corr-1");

    assertEquals(2025, resp.taxYear());
    assertEquals("PROCESSING", resp.status());
    assertEquals(new BigDecimal("999.99"), resp.expectedAmount());
    assertEquals("IRS-1", resp.trackingId());
    assertEquals(predictedAt, resp.availableAtEstimated());
    assertNull(resp.aiExplanation());

    verify(irs, times(1)).fetchMostRecentRefund(1L);
    verify(persistenceService, times(1))
        .upsertLatestFromIrs(eq(1L), any(IrsAdapter.IrsRefundResult.class), eq("refund:latest:1"));

    // cached response written
    verify(valueOps, times(1)).set(eq("refund:latest:1"), anyString(), eq(Duration.ofSeconds(60)));

    // audit written (success=true)
    verify(auditRepo, times(1)).save(argThat(a ->
        a.getUserId().equals(1L)
            && a.getEndpoint().equals("GET /api/refund/latest")
            && a.isSuccess()
            && "corr-1".equals(a.getCorrelationId())
    ));
  }

  @Test
  void latest_returnsCachedResponse_whenCacheHit_andAuditsSuccess() throws Exception {
    IrsAdapter irs = mock(IrsAdapter.class);
    RefundStatusPersistenceService persistenceService = mock(RefundStatusPersistenceService.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    String cachedJson = objectMapper.writeValueAsString(new RefundStatusResponse(
        2025,
        "AVAILABLE",
        Instant.now(),
        new BigDecimal("123.45"),
        "IRS-CACHED",
        null,
        null
    ));
    when(valueOps.get("refund:latest:1")).thenReturn(cachedJson);

    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    RefundService svc = newSvc(irs, persistenceService, auditRepo, redis, objectMapper);

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal, "corr-3");

    assertEquals(2025, resp.taxYear());
    assertEquals("AVAILABLE", resp.status());
    assertEquals(new BigDecimal("123.45"), resp.expectedAmount());
    assertEquals("IRS-CACHED", resp.trackingId());

    // No downstream calls on cache hit
    verifyNoInteractions(irs);
    verifyNoInteractions(persistenceService);

    // should not overwrite cache on cache hit
    verify(valueOps, never()).set(anyString(), anyString(), any());

    // audit written (success=true)
    verify(auditRepo, times(1)).save(argThat(a ->
        a.getUserId().equals(1L)
            && a.getEndpoint().equals("GET /api/refund/latest")
            && a.isSuccess()
            && "corr-3".equals(a.getCorrelationId())
    ));
  }

  @Test
  void latest_whenIrsFetchThrows_stillAuditsFailure() throws Exception {
    IrsAdapter irs = mock(IrsAdapter.class);
    RefundStatusPersistenceService persistenceService = mock(RefundStatusPersistenceService.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null);

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    RefundService svc = newSvc(irs, persistenceService, auditRepo, redis, objectMapper);

    when(irs.fetchMostRecentRefund(1L)).thenThrow(new RuntimeException("IRS down"));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");

    assertThrows(RuntimeException.class, () -> svc.getLatestRefundStatus(principal, "corr-fail"));

    verifyNoInteractions(persistenceService);

    // audit written (success=false)
    verify(auditRepo, times(1)).save(argThat(a ->
        a.getUserId().equals(1L)
            && a.getEndpoint().equals("GET /api/refund/latest")
            && !a.isSuccess()
            && "corr-fail".equals(a.getCorrelationId())
    ));
  }

  @Test
  void latest_whenPersistenceThrows_stillAuditsFailure_andDoesNotCache() throws Exception {
    IrsAdapter irs = mock(IrsAdapter.class);
    RefundStatusPersistenceService persistenceService = mock(RefundStatusPersistenceService.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null);

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RefundService svc = newSvc(irs, persistenceService, auditRepo, redis, objectMapper);

    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, com.intuit.taxrefund.refund.model.RefundStatus.PROCESSING, new BigDecimal("10.00"), "IRS-ERR"
    ));
    when(persistenceService.upsertLatestFromIrs(eq(1L), any(IrsAdapter.IrsRefundResult.class), eq("refund:latest:1")))
        .thenThrow(new RuntimeException("DB failed"));

    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");

    assertThrows(RuntimeException.class, () -> svc.getLatestRefundStatus(principal, "corr-persist-fail"));

    verify(valueOps, never()).set(anyString(), anyString(), any());

    verify(auditRepo, times(1)).save(argThat(a ->
        a.getUserId().equals(1L)
            && a.getEndpoint().equals("GET /api/refund/latest")
            && !a.isSuccess()
            && "corr-persist-fail".equals(a.getCorrelationId())
    ));
  }
}