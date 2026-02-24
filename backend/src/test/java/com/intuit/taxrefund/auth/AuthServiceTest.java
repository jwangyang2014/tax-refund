package com.intuit.taxrefund.auth;

import com.intuit.taxrefund.auth.controller.dto.LoginRequest;
import com.intuit.taxrefund.auth.controller.dto.RegisterRequest;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.RefreshToken;
import com.intuit.taxrefund.auth.model.Role;
import com.intuit.taxrefund.auth.repository.RefreshTokenRepository;
import com.intuit.taxrefund.auth.repository.UserRepository;
import com.intuit.taxrefund.auth.service.AuthService;
import com.intuit.taxrefund.auth.service.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

  private static RegisterRequest registerReq(String email) {
    return new RegisterRequest(
        email,
        "Password123!",
        "Yang",
        "Wang",
        null,                // address optional
        "Mountain View",
        "ca",                // lowercase on purpose; service should normalize to CA
        "555-555-5555"
    );
  }

  @Test
  void register_createsUser_whenEmailNotUsed() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);
    PasswordPolicy policy = new PasswordPolicy();
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    when(userRepo.existsByEmailIgnoreCase("a@b.com")).thenReturn(false);
    when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, policy, passwordEncoder, 14);

    AppUser created = svc.register(registerReq("A@B.com"));

    assertEquals("a@b.com", created.getEmail());
    assertEquals(Role.USER, created.getRole());
    assertNotNull(created.getPasswordHash());

    // new fields
    assertEquals("Yang", created.getFirstName());
    assertEquals("Wang", created.getLastName());
    assertNull(created.getAddress());
    assertEquals("Mountain View", created.getCity());
    assertEquals("CA", created.getState()); // normalized
    assertEquals("555-555-5555", created.getPhone());

    verify(userRepo).save(any(AppUser.class));
  }

  @Test
  void register_rejectsDuplicateEmail() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    when(userRepo.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), passwordEncoder, 14);

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> svc.register(registerReq("a@b.com"))
    );
    assertEquals("Email already registered", ex.getMessage());
  }

  @Test
  void login_issuesAccessToken_andRefreshToken() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    String rawPassword = "Password123!";
    String bcryptHash = passwordEncoder.encode(rawPassword);

    AppUser user = new AppUser(
        "u1@example.com",
        bcryptHash,
        "Yang",
        "Wang",
        null,
        "Mountain View",
        "CA",
        "555-555-5555",
        Role.USER
    );
    user.setIdForTest(1L);

    when(userRepo.findByEmailIgnoreCase("u1@example.com")).thenReturn(Optional.of(user));
    when(jwtService.createAccessToken(1L, "u1@example.com", "USER")).thenReturn("access.jwt");

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    when(refreshRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), passwordEncoder, 14);

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
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    when(userRepo.findByEmailIgnoreCase("x@y.com")).thenReturn(Optional.empty());

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), passwordEncoder, 14);

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> svc.login(new LoginRequest("x@y.com", "Password123!"))
    );
    assertEquals("Bad credentials", ex.getMessage());
  }

  @Test
  void refresh_rotatesToken_whenValid() {
    UserRepository userRepo = mock(UserRepository.class);
    RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
    JwtService jwtService = mock(JwtService.class);
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AppUser user = new AppUser(
        "u1@example.com",
        passwordEncoder.encode("Password123!"),
        "Yang",
        "Wang",
        null,
        "Mountain View",
        "CA",
        "555-555-5555",
        Role.USER
    );
    user.setIdForTest(1L);

    String jti = "1234567890-abcdef";
    String raw = "randompart." + jti;

    String storedHash = sha256Base64(raw);
    RefreshToken stored = new RefreshToken(user, storedHash, jti, Instant.now().plus(Duration.ofDays(3)));

    when(refreshRepo.findByJti(jti)).thenReturn(Optional.of(stored));
    when(jwtService.createAccessToken(1L, "u1@example.com", "USER")).thenReturn("access.jwt");
    when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), passwordEncoder, 14);

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
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AppUser user = new AppUser(
        "u1@example.com",
        passwordEncoder.encode("Password123!"),
        "Yang",
        "Wang",
        null,
        "Mountain View",
        "CA",
        "555-555-5555",
        Role.USER
    );
    user.setIdForTest(1L);

    String jti = "1234567890-abcdef";
    String storedRaw = "legit." + jti;
    String attackerRaw = "stolenDifferent." + jti;

    RefreshToken stored = new RefreshToken(user, sha256Base64(storedRaw), jti, Instant.now().plus(Duration.ofDays(3)));
    when(refreshRepo.findByJti(jti)).thenReturn(Optional.of(stored));
    when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

    AuthService svc = new AuthService(userRepo, refreshRepo, jwtService, new PasswordPolicy(), passwordEncoder, 14);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.refresh(attackerRaw));
    assertEquals("Invalid refresh token", ex.getMessage());
    assertTrue(stored.isRevoked(), "replay attempt should revoke stored token");
  }

  private static String sha256Base64(String input) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}