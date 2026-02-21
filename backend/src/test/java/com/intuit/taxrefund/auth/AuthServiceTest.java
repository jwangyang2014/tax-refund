package com.intuit.taxrefund.auth;

import com.intuit.taxrefund.auth.api.dto.LoginRequest;
import com.intuit.taxrefund.auth.api.dto.RegisterRequest;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.RefreshToken;
import com.intuit.taxrefund.auth.model.Role;
import com.intuit.taxrefund.auth.repo.RefreshTokenRepository;
import com.intuit.taxrefund.auth.repo.UserRepository;
import com.intuit.taxrefund.auth.service.AuthService;
import com.intuit.taxrefund.auth.service.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

  @Test
  void register_createsUser_whenEmailNotUsed() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);
    PasswordPolicy policy = new PasswordPolicy();

    when(userRepo.existsByEmailIgnoreCase("a@b.com")).thenReturn(false);
    when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, policy, 14);

    AppUser created = svc.register(new RegisterRequest("A@B.com", "Password123!"));

    assertEquals("a@b.com", created.getEmail());
    assertEquals(Role.USER, created.getRole());
    assertNotNull(created.getPasswordHash());
    verify(userRepo).save(any(AppUser.class));
  }

  @Test
  void register_rejectsDuplicateEmail() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);

    when(userRepo.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), 14);

    var ex = assertThrows(IllegalArgumentException.class,
        () -> svc.register(new RegisterRequest("a@b.com", "Password123!")));
    assertEquals("Email already registered", ex.getMessage());
  }

  @Test
  void login_issuesAccessToken_andRefreshToken() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);

    // Generate a real bcrypt hash for the password we will use.
    String rawPassword = "Password123!";
    String bcryptHash = new BCryptPasswordEncoder().encode(rawPassword);

    AppUser user = new AppUser("u1@example.com", bcryptHash, Role.USER);
    user.setIdForTest(1L);

    when(userRepo.findByEmailIgnoreCase("u1@example.com")).thenReturn(Optional.of(user));
    when(jwtService.createAccessToken(1L, "u1@example.com", "USER")).thenReturn("access.jwt");

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    when(refreshRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), 14);

    AuthService.AuthTokens tokens = svc.login(new LoginRequest("u1@example.com", rawPassword));

    assertEquals("access.jwt", tokens.accessToken());
    assertNotNull(tokens.refreshToken());
    assertTrue(tokens.refreshMaxAge().compareTo(Duration.ofDays(1)) > 0);

    RefreshToken saved = captor.getValue();
    assertEquals("u1@example.com", saved.getUser().getEmail());
    assertFalse(saved.isRevoked());
    assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
    assertTrue(saved.getJti().length() >= 10);
  }

  @Test
  void login_rejectsBadCredentials() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);

    when(userRepo.findByEmailIgnoreCase("x@y.com")).thenReturn(Optional.empty());

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), 14);

    var ex = assertThrows(IllegalArgumentException.class,
        () -> svc.login(new LoginRequest("x@y.com", "Password123!")));
    assertEquals("Bad credentials", ex.getMessage());
  }

  @Test
  void refresh_rotatesToken_whenValid() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);

    AppUser user = new AppUser("u1@example.com",
        new BCryptPasswordEncoder().encode("Password123!"),
        Role.USER);
    user.setIdForTest(1L);

    String jti = "1234567890-abcdef";
    String raw = "randompart." + jti;

    String storedHash = sha256Base64(raw);
    RefreshToken stored = new RefreshToken(user, storedHash, jti, Instant.now().plus(Duration.ofDays(3)));

    when(refreshRepo.findByJti(jti)).thenReturn(Optional.of(stored));
    when(jwtService.createAccessToken(1L, "u1@example.com", "USER")).thenReturn("access.jwt");
    when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), 14);

    AuthService.AuthTokens rotated = svc.refresh(raw);

    assertEquals("access.jwt", rotated.accessToken());
    assertNotNull(rotated.refreshToken());
    assertNotEquals(raw, rotated.refreshToken(), "refresh token must rotate");
    assertTrue(stored.isRevoked(), "old stored token should be revoked");
  }

  @Test
  void refresh_detectsReplay_andRevokes() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);

    AppUser user = new AppUser("u1@example.com",
        new BCryptPasswordEncoder().encode("Password123!"),
        Role.USER);
    user.setIdForTest(1L);

    String jti = "1234567890-abcdef";
    String storedRaw = "legit." + jti;
    String attackerRaw = "stolenDifferent." + jti;

    RefreshToken stored = new RefreshToken(user, sha256Base64(storedRaw), jti, Instant.now().plus(Duration.ofDays(3)));
    when(refreshRepo.findByJti(jti)).thenReturn(Optional.of(stored));
    when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), 14);

    var ex = assertThrows(IllegalArgumentException.class, () -> svc.refresh(attackerRaw));
    assertEquals("Invalid refresh token", ex.getMessage());
    assertTrue(stored.isRevoked(), "replay attempt should revoke stored token");
  }

  private static String sha256Base64(String input) {
    try {
      var md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}