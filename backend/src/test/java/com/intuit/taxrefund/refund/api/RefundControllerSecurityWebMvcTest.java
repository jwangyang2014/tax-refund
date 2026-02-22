package com.intuit.taxrefund.refund.api;

import com.intuit.taxrefund.auth.SecurityConfig;
import com.intuit.taxrefund.auth.jwt.JwtAuthenticationFilter;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.config.DemoProps;
import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.service.MockIrsAdapter;
import com.intuit.taxrefund.refund.service.RefundService;
import com.intuit.taxrefund.ratelimit.RateLimitProps;
import com.intuit.taxrefund.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RefundController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RefundControllerSecurityWebMvcTest {

  @Autowired MockMvc mvc;

  @MockBean RefundService refundService;
  @MockBean MockIrsAdapter mockIrsAdapter;

  @MockBean JwtService jwtService;

  @MockBean
  StringRedisTemplate redis;

  // NEW: RefundController constructor now requires DemoProps
  @MockBean DemoProps demoProps;

  // satisfy RateLimitFilter constructor deps in WebMvc slice
  @MockBean RateLimitProps rateLimitProps;
  @MockBean RedisRateLimiter redisRateLimiter;

  @BeforeEach
  void disableRateLimiting() {
    when(rateLimitProps.enabled()).thenReturn(false);
  }

  @Test
  void latest_requiresAuth_returns401_whenNoBearer() throws Exception {
    mvc.perform(get("/api/refund/latest"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void latest_allows_whenBearerValid() throws Exception {
    when(jwtService.parseAndValidate("good-token"))
        .thenReturn(new JwtService.JwtPrincipal(1L, "u1@example.com", "USER"));

    Instant now = Instant.now();

    // UPDATED: RefundService method signature now includes correlationId
    when(refundService.getLatestRefundStatus(any(JwtService.JwtPrincipal.class), any()))
        .thenReturn(new RefundStatusResponse(
            2025,                      // taxYear
            "PROCESSING",              // status
            now,                       // lastUpdatedAt
            new BigDecimal("999.99"),  // expectedAmount
            "IRS-1",                   // trackingId
            now.plusSeconds(7L * 24 * 3600), // availableAtEstimated
            null                       // aiExplanation (controller returns whatever service returns; your service sets null)
        ));

    mvc.perform(get("/api/refund/latest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
        .andExpect(status().isOk());
  }

  @Test
  void latest_passesCorrelationIdHeader_toService() throws Exception {
    when(jwtService.parseAndValidate("good-token"))
        .thenReturn(new JwtService.JwtPrincipal(1L, "u1@example.com", "USER"));

    Instant now = Instant.now();

    when(refundService.getLatestRefundStatus(any(JwtService.JwtPrincipal.class), eq("corr-123")))
        .thenReturn(new RefundStatusResponse(
            2025,
            "PROCESSING",
            now,
            new BigDecimal("999.99"),
            "IRS-1",
            now.plusSeconds(3600),
            null
        ));

    mvc.perform(get("/api/refund/latest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
            .header("X-Correlation-Id", "corr-123"))
        .andExpect(status().isOk());
  }
}