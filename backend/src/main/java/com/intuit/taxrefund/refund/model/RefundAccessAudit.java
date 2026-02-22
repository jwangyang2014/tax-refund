package com.intuit.taxrefund.refund.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "refund_access_audit",
    indexes = {
        @Index(name = "ix_refund_audit_user_time", columnList = "user_id,occurred_at"),
        @Index(name = "ix_refund_audit_time", columnList = "occurred_at")
    }
)
public class RefundAccessAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 120)
    private String endpoint;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false, name = "occurred_at")
    private Instant occurredAt = Instant.now();

    @Column(name = "correlation_id", length = 80)
    private String correlationId;

    protected RefundAccessAudit() {}

    private RefundAccessAudit(Long userId, String endpoint, boolean success, String correlationId) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.success = success;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    public static RefundAccessAudit of(Long userId, String endpoint, boolean success, String correlationId) {
        return new RefundAccessAudit(userId, endpoint, success, correlationId);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public boolean isSuccess() { return success; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
}