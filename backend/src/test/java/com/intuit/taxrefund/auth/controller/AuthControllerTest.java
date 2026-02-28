package com.intuit.taxrefund.auth.controller;

import com.intuit.taxrefund.auth.AuthPrincipalSupport;
import com.intuit.taxrefund.auth.CookieService;
import com.intuit.taxrefund.auth.controller.dto.LoginRequest;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.service.AuthService;
import com.intuit.taxrefund.shared.ratelimit.RateLimitProps;
import com.intuit.taxrefund.shared.ratelimit.RedisRateLimiter;
import com.intuit.taxrefund.shared.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // keep it focused on controller behavior
@Import(GlobalExceptionHandler.class)     // so 400 becomes JSON, not an exception
class AuthControllerTest {

  @Autowired
  MockMvc mvc;

  @MockBean
  AuthService authService;

  @MockBean
  CookieService cookieService;

  @MockBean
  AuthPrincipalSupport authPrincipalSupport;   // <-- added fix

  // Optional when addFilters=false, but keeping it is fine.
  @MockBean
  JwtService jwtService;

  // Satisfy RateLimitFilter constructor deps even in @WebMvcTest slice
  @MockBean
  RateLimitProps rateLimitProps;

  @MockBean
  RedisRateLimiter redisRateLimiter;

  @Test
  void login_setsRefreshCookie_andReturnsAccessToken() throws Exception {
    when(authService.login(any(LoginRequest.class)))
        .thenReturn(new AuthService.AuthTokens("access.jwt", "refresh.raw", Duration.ofDays(14)));

    doNothing().when(cookieService).setRefreshCookie(any(HttpServletResponse.class), anyString(), anyLong());

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"u1@example.com","password":"Password123!"}
                """))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").value("access.jwt"));

    ArgumentCaptor<String> refreshCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> ageSecondsCaptor = ArgumentCaptor.forClass(Long.class);

    verify(cookieService).setRefreshCookie(
        any(HttpServletResponse.class),
        refreshCaptor.capture(),
        ageSecondsCaptor.capture()
    );

    assertEquals("refresh.raw", refreshCaptor.getValue());
    assertEquals(Duration.ofDays(14).toSeconds(), ageSecondsCaptor.getValue());
  }

  @Test
  void refresh_readsCookie_rotatesCookie_andReturnsNewAccessToken() throws Exception {
    when(cookieService.refreshCookieName()).thenReturn("refresh_token");

    when(authService.refresh("old.refresh"))
        .thenReturn(new AuthService.AuthTokens("new.access", "new.refresh", Duration.ofDays(14)));

    doNothing().when(cookieService).setRefreshCookie(any(HttpServletResponse.class), anyString(), anyLong());

    mvc.perform(post("/api/auth/refresh")
            .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old.refresh")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").value("new.access"));

    verify(authService).refresh("old.refresh");

    ArgumentCaptor<String> refreshCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> ageSecondsCaptor = ArgumentCaptor.forClass(Long.class);

    verify(cookieService).setRefreshCookie(
        any(HttpServletResponse.class),
        refreshCaptor.capture(),
        ageSecondsCaptor.capture()
    );

    assertEquals("new.refresh", refreshCaptor.getValue(), "should set rotated refresh token");
    assertEquals(Duration.ofDays(14).toSeconds(), ageSecondsCaptor.getValue());
  }

  @Test
  void logout_clearsRefreshCookie() throws Exception {
    when(cookieService.refreshCookieName()).thenReturn("refresh_token");
    doNothing().when(cookieService).clearRefreshCookie(any(HttpServletResponse.class));

    mvc.perform(post("/api/auth/logout")
            .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some.refresh")))
        .andExpect(status().isOk());

    verify(authService).logout("some.refresh");
    verify(cookieService).clearRefreshCookie(any(HttpServletResponse.class));
  }

  @Test
  void refresh_returns400_whenCookieMissing() throws Exception {
    when(cookieService.refreshCookieName()).thenReturn("refresh_token");

    mvc.perform(post("/api/auth/refresh"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Missing refresh token"))
        .andExpect(jsonPath("$.path").value("/api/auth/refresh"));
  }
}