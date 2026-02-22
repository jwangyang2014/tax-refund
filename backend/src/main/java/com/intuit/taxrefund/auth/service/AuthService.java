package com.intuit.taxrefund.auth.service;

import com.intuit.taxrefund.auth.api.dto.LoginRequest;
import com.intuit.taxrefund.auth.api.dto.RegisterRequest;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.RefreshToken;
import com.intuit.taxrefund.auth.model.Role;
import com.intuit.taxrefund.auth.repo.RefreshTokenRepository;
import com.intuit.taxrefund.auth.repo.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LogManager.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final long refreshTokenDays;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
        UserRepository userRepo,
        RefreshTokenRepository refreshRepo,
        JwtService jwtService,
        PasswordPolicy passwordPolicy,
        @Value("${app.security.jwt.refreshTokenDays}") long refreshTokenDays
    ) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;

        log.info("auth_service_initialized refreshTokenDays={}", refreshTokenDays);
    }

    public AppUser register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (this.userRepo.existsByEmailIgnoreCase(email)) {
            log.warn("register_email_exists email={}", maskEmail(email));
            throw new IllegalArgumentException("Email already registered");
        }

        String password = request.password();
        passwordPolicy.validate(password);

        String encodedPassword = passwordEncoder.encode(password);

        AppUser user = new AppUser(
            email,
            encodedPassword,
            request.firstName().trim(),
            request.lastName().trim(),
            normalizeOptional(request.address()),
            request.city().trim(),
            request.state().trim().toUpperCase(),
            request.phone().trim(),
            Role.USER
        );

        AppUser saved = userRepo.save(user);
        log.info("register_success userId={} email={}", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
    }

    public AuthTokens login(LoginRequest request) {
        AppUser user = userRepo.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> {
                log.warn("login_bad_credentials email={}", maskEmail(request.email()));
                return new IllegalArgumentException("Bad credentials");
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("login_bad_credentials userId={} email={}", user.getId(), maskEmail(user.getEmail()));
            throw new IllegalArgumentException("Bad credentials");
        }

        AuthTokens tokens = issueTokens(user, true);
        log.info("login_issued_tokens userId={} role={}", user.getId(), user.getRole());
        return tokens;
    }

    public void logout(String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            log.debug("logout_no_cookie");
            return;
        }

        try {
            ParseRefreshToken parsed = ParseRefreshToken.parse(refreshCookie);
            this.refreshRepo.findByJti(parsed.jti()).ifPresent(rt -> {
                rt.revoke();
                this.refreshRepo.save(rt);
                log.info("logout_revoked_refresh jti={}", parsed.jti());
            });
        } catch (Exception e) {
            log.warn("logout_parse_failed err={}", e.toString());
        }
    }

    public AuthTokens refresh(String refreshCookie) {
        ParseRefreshToken parsed = ParseRefreshToken.parse(refreshCookie);

        RefreshToken stored = refreshRepo.findByJti(parsed.jti())
            .orElseThrow(() -> {
                log.warn("refresh_invalid_jti jti={}", parsed.jti());
                return new IllegalArgumentException("Invalid refresh token");
            });

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            log.warn("refresh_revoked_or_expired jti={} revoked={} expiresAt={}",
                parsed.jti(), stored.isRevoked(), stored.getExpiresAt());
            throw new IllegalArgumentException("Refresh token revoked or expired");
        }

        // Rotate
        stored.revoke();
        refreshRepo.save(stored);

        String refreshInputHash = sha256Base64(refreshCookie);
        if (!refreshInputHash.equals(stored.getTokenHash())) {
            log.warn("refresh_hash_mismatch jti={}", parsed.jti());
            throw new IllegalArgumentException("Invalid refresh token");
        }

        AuthTokens tokens = issueTokens(stored.getUser(), true);
        log.info("refresh_success userId={} rotated=true", stored.getUser().getId());
        return tokens;
    }

    public record AuthTokens(
        String accessToken,
        String refreshToken,
        Duration refreshMaxAge
    ) {}

    private AuthTokens issueTokens(AppUser user, boolean issueRefresh) {
        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        if (!issueRefresh) return new AuthTokens(accessToken, null, Duration.ZERO);

        String jti = UUID.randomUUID().toString();
        String rawRefresh = UUID.randomUUID() + "." + jti;
        String refreshHash = sha256Base64(rawRefresh);

        Duration refreshDuration = Duration.ofDays(refreshTokenDays);
        Instant exp = Instant.now().plus(refreshDuration);

        refreshRepo.save(new RefreshToken(user, refreshHash, jti, exp));

        log.debug("refresh_token_issued userId={} jti={} exp={}", user.getId(), jti, exp);
        return new AuthTokens(accessToken, rawRefresh, refreshDuration);
    }

    private String sha256Base64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record ParseRefreshToken(String rawToken, String jti) {
        static ParseRefreshToken parse(String token) {
            if (token == null || token.isBlank() || !token.contains(".")) {
                throw new IllegalArgumentException("Invalid refresh token");
            }

            String[] parts = token.split("\\.", 2);
            String jti = parts[1];
            if (jti.length() < 10) {
                throw new IllegalArgumentException("Invalid refresh token");
            }

            return new ParseRefreshToken(token, jti);
        }
    }

    private static String normalizeOptional(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String maskEmail(String email) {
        if (email == null) return "null";
        String e = email.trim().toLowerCase();
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.substring(0, 1) + "***" + e.substring(at);
    }
}