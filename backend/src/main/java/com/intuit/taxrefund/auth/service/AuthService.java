package com.intuit.taxrefund.auth.service;

import com.intuit.taxrefund.auth.api.dto.LoginRequest;
import com.intuit.taxrefund.auth.api.dto.RegisterRequest;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.model.RefreshToken;
import com.intuit.taxrefund.auth.model.Role;
import com.intuit.taxrefund.auth.repo.RefreshTokenRepository;
import com.intuit.taxrefund.auth.repo.UserRepository;
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
    }

    public AppUser register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (this.userRepo.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        String password = request.password();
        passwordPolicy.validate(password);
        String encodedPassword = passwordEncoder.encode(password);

        return userRepo.save(new AppUser(email, encodedPassword, Role.USER));
    }

    public AuthTokens login(LoginRequest request) {
        AppUser user = userRepo.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Bad credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Bad credentials");
        }

        return issueTokens(user, true);
    }

    public void logout(String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) return;

        try {
            ParseRefreshToken parsed = ParseRefreshToken.parse(refreshCookie);
            this.refreshRepo.findByJti(parsed.jti()).ifPresent(rt -> {
                rt.revoke();
                this.refreshRepo.save(rt);
            });
        } catch (Exception e) {
            // do nothing
        }
    }

    public AuthTokens refresh(String refreshCookie) {
        ParseRefreshToken parsed = ParseRefreshToken.parse(refreshCookie);
        RefreshToken stored = refreshRepo.findByJti(parsed.jti())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token revoked or expired");
        }

        stored.revoke();
        refreshRepo.save(stored);

        String refreshInputHash = sha256Base64(refreshCookie);
        if (!refreshInputHash.equals(stored.getTokenHash())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        return issueTokens(stored.getUser(), true);
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

        RefreshToken refreshToken = refreshRepo.save(new RefreshToken(user, refreshHash, jti, exp));

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
}
