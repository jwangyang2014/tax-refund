package com.intuit.taxrefund.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    private static final Logger log = LogManager.getLogger(JwtService.class);

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenMinutes;

    public record JwtPrincipal(Long userId, String email, String role) {}

    public JwtService(
        @Value("${app.security.jwt.secret}") String secret,
        @Value("${app.security.jwt.issuer}") String issuer,
        @Value("${app.security.jwt.accessTokenMinutes}") long accessTokenMinutes
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Jwt secret missing or less than 32 characters");
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.issuer = issuer;
        this.accessTokenMinutes = accessTokenMinutes;

        log.info("jwt_service_initialized issuer={} accessTokenMinutes={}", issuer, accessTokenMinutes);
    }

    public String createAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        Instant exp = now.plus(this.accessTokenMinutes, ChronoUnit.MINUTES);

        String jwt = Jwts.builder()
            .issuer(this.issuer)
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(this.key)
            .compact();

        log.debug("access_token_issued userId={} role={} exp={}", userId, role, exp);
        return jwt;
    }

    public JwtPrincipal parseAndValidate(String jwt) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(this.key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            return new JwtPrincipal(userId, email, role);
        } catch (Exception e) {
            log.warn("jwt_parse_failed err={}", e.toString());
            throw e;
        }
    }
}