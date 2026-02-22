package com.intuit.taxrefund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.ai.AiConfig;
import com.intuit.taxrefund.ai.model.AiRequestLog;
import com.intuit.taxrefund.ai.repo.AiRequestLogRepository;
import com.intuit.taxrefund.ai.service.AiClient;
import com.intuit.taxrefund.ai.service.AiClientRouter;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.Role;
import com.intuit.taxrefund.auth.repo.UserRepository;
import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.model.RefundRecord;
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.repo.RefundRecordRepository;
import com.intuit.taxrefund.refund.service.IrsAdapter;
import com.intuit.taxrefund.refund.service.RefundService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

  @Test
  void latest_createsRecordIfMissing_andCallsAI_whenNotAvailable() throws Exception {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    AiClient ai = mock(AiClient.class);
    AiClientRouter router = mock(AiClientRouter.class);
    when(router.getClient()).thenReturn(ai);

    AiRequestLogRepository aiLogRepo = mock(AiRequestLogRepository.class);
    AiConfig cfg = new AiConfig();
    cfg.setProvider("mock");

    // Redis mocking (opsForValue used)
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null); // no cache hit

    ObjectMapper objectMapper = new ObjectMapper();

    RefundService svc = new RefundService(refundRepo, userRepo, irs, router, aiLogRepo, cfg, redis, objectMapper);

    AppUser user = user1();

    when(userRepo.findById(1L)).thenReturn(Optional.of(user));
    when(refundRepo.findByUserIdAndTaxYear(1L, 2025)).thenReturn(Optional.empty());

    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, RefundStatus.PROCESSING, new BigDecimal("999.99"), "IRS-1"
    ));

    // RefundService logs aiClient.getModel()
    when(ai.getModel()).thenReturn("mock-eta-v1");

    when(ai.predictRefundEtaDays(eq(RefundStatus.PROCESSING), any(BigDecimal.class)))
        .thenReturn(new AiClient.PredictEtaResult(
            7,
            "Based on processing stage",
            "mock",
            "mock-eta-v1"
        ));

    ArgumentCaptor<RefundRecord> recordCaptor = ArgumentCaptor.forClass(RefundRecord.class);
    when(refundRepo.save(recordCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    ArgumentCaptor<AiRequestLog> logCaptor = ArgumentCaptor.forClass(AiRequestLog.class);
    when(aiLogRepo.save(logCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal);

    assertEquals(2025, resp.taxYear());
    assertEquals("PROCESSING", resp.status());
    assertEquals(new BigDecimal("999.99"), resp.expectedAmount());
    assertEquals("IRS-1", resp.trackingId());
    assertNotNull(resp.availableAtEstimated());
    assertTrue(resp.availableAtEstimated().isAfter(Instant.now()));
    assertEquals("Based on processing stage", resp.aiExplanation());

    RefundRecord saved = recordCaptor.getValue();
    assertEquals(RefundStatus.PROCESSING, saved.getStatus());
    assertEquals("IRS-1", saved.getIrsTrackingId());
    assertNotNull(saved.getAvailableAtEstimated());

    verify(ai, times(1)).predictRefundEtaDays(eq(RefundStatus.PROCESSING), any(BigDecimal.class));

    AiRequestLog log = logCaptor.getValue();
    assertTrue(log.isSuccess());
    assertEquals("mock", log.getProvider());

    // verify cache write (TTL 60s)
    verify(valueOps, times(1)).set(eq("refund:latest:1"), anyString(), eq(Duration.ofSeconds(60)));
  }

  @Test
  void latest_doesNotCallAI_whenAvailable() throws Exception {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    AiClient ai = mock(AiClient.class);
    AiClientRouter router = mock(AiClientRouter.class);
    when(router.getClient()).thenReturn(ai);

    AiRequestLogRepository aiLogRepo = mock(AiRequestLogRepository.class);
    AiConfig cfg = new AiConfig();

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("refund:latest:1")).thenReturn(null); // no cache hit

    ObjectMapper objectMapper = new ObjectMapper();

    RefundService svc = new RefundService(refundRepo, userRepo, irs, router, aiLogRepo, cfg, redis, objectMapper);

    AppUser user = user1();

    RefundRecord existing = new RefundRecord(user, 2025, RefundStatus.RECEIVED);
    when(refundRepo.findByUserIdAndTaxYear(1L, 2025)).thenReturn(Optional.of(existing));

    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, RefundStatus.AVAILABLE, new BigDecimal("500.00"), "IRS-AVAIL"
    ));

    when(refundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    JwtService.JwtPrincipal principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    RefundStatusResponse resp = svc.getLatestRefundStatus(principal);

    assertEquals("AVAILABLE", resp.status());
    assertNull(resp.aiExplanation());
    assertNull(resp.availableAtEstimated());

    verify(ai, never()).predictRefundEtaDays(any(), any());
    verify(aiLogRepo, never()).save(any());

    verify(valueOps, times(1)).set(eq("refund:latest:1"), anyString(), eq(Duration.ofSeconds(60)));
  }
}