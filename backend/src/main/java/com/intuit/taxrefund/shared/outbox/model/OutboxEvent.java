package com.intuit.taxrefund.shared.outbox.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
    name = "outbox_event",
    indexes = {
        @Index(name = "ix_outbox_created", columnList = "created_at"),
        @Index(name = "ix_outbox_unprocessed", columnList = "processed_at")
    }
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "event_type", length = 80)
    private String eventType;

    @Column(nullable = false, name = "aggregate_key", length = 120)
    private String aggregateKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 80)
    private String lockedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEvent() {}

    private OutboxEvent(String eventType, String aggregateKey, String payload) {
        this.eventType = eventType;
        this.aggregateKey = aggregateKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public static OutboxEvent newEvent(String eventType, String aggregateKey, String payload) {
        return new OutboxEvent(eventType, aggregateKey, payload);
    }

    public void markProcessed() {
        this.processedAt = Instant.now();
    }

    public void bumpAttempt(String err) {
        this.attempts++;
        this.lastError = err;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateKey() { return aggregateKey; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLockedAt() { return lockedAt; }
    public String getLockedBy() { return lockedBy; }
    public Instant getProcessedAt() { return processedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }

    public void lock(String workerId) {
        this.lockedAt = Instant.now();
        this.lockedBy = workerId;
    }

    public void unlock() {
        this.lockedAt = null;
        this.lockedBy = null;
    }
}