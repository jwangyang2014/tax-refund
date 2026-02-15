package com.intuit.taxrefundstatus.auth.model;

import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, name = "token_hash", length = 255)
    private String tokenHash;

    @Column(nullable = false, unique = true, length = 80)
    private String jti;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(AppUser user, String tokenHash, String jti, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void revoke() {
        this.revoked = true;
    }
}
