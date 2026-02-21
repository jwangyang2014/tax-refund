package com.intuit.taxrefund;

import com.intuit.taxrefund.ai.AiConfig;
import com.intuit.taxrefund.ai.model.AiRequestLog;
import com.intuit.taxrefund.ai.repo.AiRequestLogRepository;
import com.intuit.taxrefund.ai.service.AiClient;
import com.intuit.taxrefund.ai.service.AiClientRouter;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.Role;
import com.intuit.taxrefund.auth.repo.UserRepository;
import com.intuit.taxrefund.refund.model.RefundRecord;
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.repo.RefundRecordRepository;
import com.intuit.taxrefund.refund.service.IrsAdapter;
import com.intuit.taxrefund.refund.service.RefundService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {

  @Test
  void latest_createsRecordIfMissing_andCallsAI_whenNotAvailable() {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    AiClient ai = mock(AiClient.class);
    AiClientRouter router = mock(AiClientRouter.class);
    when(router.getClient()).thenReturn(ai);

    AiRequestLogRepository aiLogRepo = mock(AiRequestLogRepository.class);
    AiConfig cfg = new AiConfig();
    cfg.setProvider("mock");

    RefundService svc = new RefundService(refundRepo, userRepo, irs, router, aiLogRepo, cfg);

    AppUser user = new AppUser("u1@example.com", "hash", Role.USER);
    user.setIdForTest(1L);

    when(userRepo.findById(1L)).thenReturn(Optional.of(user));
    when(refundRepo.findByUserIdAndTaxYear(1L, 2025)).thenReturn(Optional.empty());

    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, RefundStatus.PROCESSING, new BigDecimal("999.99"), "IRS-1"
    ));

    when(ai.predictRefundEtaDays(eq(RefundStatus.PROCESSING), any(BigDecimal.class)))
        .thenReturn(new AiClient.PredictEtaResult(7, "Based on processing stage", "mock", "mock-eta-v1"));

    ArgumentCaptor<RefundRecord> recordCaptor = ArgumentCaptor.forClass(RefundRecord.class);
    when(refundRepo.save(recordCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    ArgumentCaptor<AiRequestLog> logCaptor = ArgumentCaptor.forClass(AiRequestLog.class);
    when(aiLogRepo.save(logCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    var principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    var resp = svc.getLatestRefundStatus(principal);

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
    // NOTE: don't assert model here unless you're sure RefundService sets it on AiRequestLog
  }

  @Test
  void latest_doesNotCallAI_whenAvailable() {
    RefundRecordRepository refundRepo = mock(RefundRecordRepository.class);
    UserRepository userRepo = mock(UserRepository.class);
    IrsAdapter irs = mock(IrsAdapter.class);

    AiClient ai = mock(AiClient.class);
    AiClientRouter router = mock(AiClientRouter.class);
    when(router.getClient()).thenReturn(ai); // may be called in constructor

    AiRequestLogRepository aiLogRepo = mock(AiRequestLogRepository.class);
    AiConfig cfg = new AiConfig();

    RefundService svc = new RefundService(refundRepo, userRepo, irs, router, aiLogRepo, cfg);

    AppUser user = new AppUser("u1@example.com", "hash", Role.USER);
    user.setIdForTest(1L);

    RefundRecord existing = new RefundRecord(user, 2025, RefundStatus.RECEIVED);
    when(refundRepo.findByUserIdAndTaxYear(1L, 2025)).thenReturn(Optional.of(existing));

    when(irs.fetchMostRecentRefund(1L)).thenReturn(new IrsAdapter.IrsRefundResult(
        2025, RefundStatus.AVAILABLE, new BigDecimal("500.00"), "IRS-AVAIL"
    ));

    when(refundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var principal = new JwtService.JwtPrincipal(1L, "u1@example.com", "USER");
    var resp = svc.getLatestRefundStatus(principal);

    assertEquals("AVAILABLE", resp.status());
    assertNull(resp.aiExplanation());
    assertNull(resp.availableAtEstimated());

    // Key assertion: AI should not be invoked for AVAILABLE
    verify(ai, never()).predictRefundEtaDays(any(), any());
    verify(aiLogRepo, never()).save(any());
  }
}