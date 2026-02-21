package com.intuit.taxrefund.refund.api;

import com.intuit.taxrefund.auth.SecurityConfig;
import com.intuit.taxrefund.auth.jwt.JwtAuthenticationFilter;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.refund.service.MockIrsAdapter;
import com.intuit.taxrefund.refund.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

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

  @Test
  void latest_requiresAuth_returns403_whenNoBearer() throws Exception {
    mvc.perform(get("/api/refund/latest"))
        .andExpect(status().isForbidden());
  }

  @Test
  void latest_allows_whenBearerValid() throws Exception {
    when(jwtService.parseAndValidate("good-token"))
        .thenReturn(new JwtService.JwtPrincipal(1L, "u1@example.com", "USER"));

    // If your controller calls refundService.getLatestRefundStatus(principal), stub it here to return something.
    // Otherwise it may NPE/throw and you won't get 200.
    // when(refundService.getLatestRefundStatus(any())).thenReturn(...);

    mvc.perform(get("/api/refund/latest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
        .andExpect(status().isOk());
  }
}