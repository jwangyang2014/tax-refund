package com.intuit.taxrefundstatus.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
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
    }

    public String createAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        Instant exp = now.plus(this.accessTokenMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .issuer(this.issuer)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(this.key)
                .compact();
    }

    public JwtPrincipal parseAndValidate(String jwt) {
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

    }
}
