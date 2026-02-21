package com.intuit.taxrefund.auth.api;

import com.intuit.taxrefund.auth.CookieService;
import com.intuit.taxrefund.auth.api.dto.LoginRequest;
import com.intuit.taxrefund.auth.service.AuthService;
import com.intuit.taxrefund.auth.jwt.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // keep it focused on controller behavior
class AuthControllerWebMvcTest {

  @Autowired MockMvc mvc;

  @MockBean AuthService authService;
  @MockBean CookieService cookieService;

  // IMPORTANT:
  // @WebMvcTest includes Filter beans by default, so it will create JwtAuthenticationFilter,
  // which depends on JwtService. Mock it so the context can start.
  @MockBean JwtService jwtService;

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

    Exception ex = assertThrows(Exception.class, () ->
        mvc.perform(post("/api/auth/refresh")).andReturn()
    );

    Throwable root = ex;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    assertTrue(root instanceof IllegalArgumentException, "Expected IllegalArgumentException");
    assertEquals("Missing refresh token", root.getMessage());
  }
}