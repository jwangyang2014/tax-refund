package com.intuit.taxrefund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.ai.model.RefundEtaPrediction;
import com.intuit.taxrefund.ai.repo.RefundEtaPredictionRepository;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.Role;
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
import com.intuit.taxrefund.refund.service.IrsAdapter;
import com.intuit.taxrefund.refund.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {

  private static AppUser user1() {
    AppUser user = new AppUser(
        "u1@example.com",
        "hash",
        "Yang",
        "Wang",
        null,
        "Mountain View",
        "CA",
        "555-555-5555",
        Role.USER
    );
    user.setIdForTest(1L);
    return user;
  }

  private static RefundService newSvc(
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
    return new RefundService(
        refundRepo,
        userRepo,
        irs,
        statusEventRepo,
        outboxRepo,
        etaRepo,
        auditRepo,
        redis,
        objectMapper
    );
  }

  @Test
  void latest_whenStatusChanges_writesEventAndOutbox_deletesCache_readsEta_cachesResponse_andAuditsSuccess() throws Exception {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    RefundStatusEventRepository statusEventRepo = mock(RefundStatusEventRepository.class);
    OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
    RefundEtaPredictionRepository etaRepo = mock(RefundEtaPredictionRepository.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null); // no cache hit

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    RefundService svc = newSvc(refundRepo, userRepo, irs, statusEventRepo, outboxRepo, etaRepo, auditRepo, redis, objectMapper);

    AppUser user = user1();
    when(userRepo.findById(1L)).thenReturn(Optional.of(user));

    // Existing record oldStatus = RECEIVED
    RefundRecord existing = new RefundRecord(user, 2025, RefundStatus.RECEIVED);
    when(refundRepo.findByUserIdAndTaxYear(1L, 2025)).thenReturn(Optional.of(existing));

    // IRS says PROCESSING => status change RECEIVED -> PROCESSING
    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, RefundStatus.PROCESSING, new BigDecimal("999.99"), "IRS-1"
    ));

    // ETA prediction exists
    RefundEtaPrediction pred = mock(RefundEtaPrediction.class);
    Instant predictedAt = Instant.now().plusSeconds(7 * 86400L);
    when(pred.getEstimatedAvailableAt()).thenReturn(predictedAt);

    when(etaRepo.findTopByUserIdAndTaxYearAndStatusOrderByCreatedAtDesc(
        1L, 2025, "PROCESSING"
    )).thenReturn(Optional.of(pred));

    when(refundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(statusEventRepo.save(any(RefundStatusEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal, "corr-1");

    assertEquals(2025, resp.taxYear());
    assertEquals("PROCESSING", resp.status());
    assertEquals(new BigDecimal("999.99"), resp.expectedAmount());
    assertEquals("IRS-1", resp.trackingId());
    assertEquals(predictedAt, resp.availableAtEstimated());
    assertNull(resp.aiExplanation());

    // Event + outbox + cache invalidation on status change
    verify(statusEventRepo, times(1)).save(any(RefundStatusEvent.class));
    verify(outboxRepo, times(1)).save(any(OutboxEvent.class));
    verify(redis, times(1)).delete("refund:latest:1");

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
  void latest_whenStatusDoesNotChange_doesNotWriteEventOrOutbox_doesNotDeleteCache_stillCachesResponse_andAuditsSuccess() throws Exception {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    RefundStatusEventRepository statusEventRepo = mock(RefundStatusEventRepository.class);
    OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
    RefundEtaPredictionRepository etaRepo = mock(RefundEtaPredictionRepository.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null); // no cache hit

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    RefundService svc = newSvc(refundRepo, userRepo, irs, statusEventRepo, outboxRepo, etaRepo, auditRepo, redis, objectMapper);

    AppUser user = user1();
    when(userRepo.findById(1L)).thenReturn(Optional.of(user));

    // Existing record oldStatus = PROCESSING
    RefundRecord existing = new RefundRecord(user, 2025, RefundStatus.PROCESSING);
    when(refundRepo.findByUserIdAndTaxYear(1L, 2025)).thenReturn(Optional.of(existing));

    // IRS returns PROCESSING again => no status change
    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, RefundStatus.PROCESSING, new BigDecimal("200.00"), "IRS-X"
    ));

    // No ETA prediction
    when(etaRepo.findTopByUserIdAndTaxYearAndStatusOrderByCreatedAtDesc(
        1L, 2025, "PROCESSING"
    )).thenReturn(Optional.empty());

    when(refundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal, "corr-2");

    assertEquals("PROCESSING", resp.status());
    assertNull(resp.aiExplanation());

    // no event/outbox/invalidate
    verify(statusEventRepo, never()).save(any());
    verify(outboxRepo, never()).save(any());
    verify(redis, never()).delete(anyString());

    // caches response anyway
    verify(valueOps, times(1)).set(eq("refund:latest:1"), anyString(), eq(Duration.ofSeconds(60)));

    // audit written (success=true)
    verify(auditRepo, times(1)).save(argThat(a ->
        a.getUserId().equals(1L)
            && a.getEndpoint().equals("GET /api/refund/latest")
            && a.isSuccess()
            && "corr-2".equals(a.getCorrelationId())
    ));
  }

  @Test
  void latest_returnsCachedResponse_whenCacheHit_andAuditsSuccess() throws Exception {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    RefundStatusEventRepository statusEventRepo = mock(RefundStatusEventRepository.class);
    OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
    RefundEtaPredictionRepository etaRepo = mock(RefundEtaPredictionRepository.class);
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

    RefundService svc = newSvc(refundRepo, userRepo, irs, statusEventRepo, outboxRepo, etaRepo, auditRepo, redis, objectMapper);

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal, "corr-3");

    assertEquals(2025, resp.taxYear());
    assertEquals("AVAILABLE", resp.status());
    assertEquals(new BigDecimal("123.45"), resp.expectedAmount());
    assertEquals("IRS-CACHED", resp.trackingId());

    // No downstream calls on cache hit
    verifyNoInteractions(irs);
    verifyNoInteractions(refundRepo);
    verifyNoInteractions(statusEventRepo);
    verifyNoInteractions(outboxRepo);
    verifyNoInteractions(etaRepo);

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
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    RefundStatusEventRepository statusEventRepo = mock(RefundStatusEventRepository.class);
    OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
    RefundEtaPredictionRepository etaRepo = mock(RefundEtaPredictionRepository.class);
    RefundAccessAuditRepository auditRepo = mock(RefundAccessAuditRepository.class);

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null);

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    when(auditRepo.save(any(RefundAccessAudit.class))).thenAnswer(inv -> inv.getArgument(0));

    RefundService svc = newSvc(refundRepo, userRepo, irs, statusEventRepo, outboxRepo, etaRepo, auditRepo, redis, objectMapper);

    when(irs.fetchMostRecentRefund(1L)).thenThrow(new RuntimeException("IRS down"));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");

    assertThrows(RuntimeException.class, () -> svc.getLatestRefundStatus(principal, "corr-fail"));

    // audit written (success=false)
    verify(auditRepo, times(1)).save(argThat(a ->
        a.getUserId().equals(1L)
            && a.getEndpoint().equals("GET /api/refund/latest")
            && !a.isSuccess()
            && "corr-fail".equals(a.getCorrelationId())
    ));
  }
}