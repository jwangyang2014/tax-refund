package com.intuit.taxrefundstatus.refund.model;

import com.intuit.taxrefundstatus.auth.model.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "refund_record",
    uniqueConstraints = @UniqueConstraint(name = "uq_refund_user_year", columnNames = {"user_id", "tax_year"}),
    indexes = @Index(name = "ix_refund_user_last", columnList = "user_id, last_updated_at")
)
public class RefundRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, name = "tax_year")
    private int taxYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(nullable = false, name = "last_updated_at")
    private Instant lastUpdatedAt = Instant.now();

    @Column(name = "expected_amount", precision = 18, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "irs_tracking_id", length = 100)
    private String irsTrackingId;

    @Column(name = "available_at_estimated")
    private Instant availableAtEstimated;

    protected RefundRecord() {}

    public RefundRecord(AppUser user, int taxYear, RefundStatus status) {
        this.user = user;
        this.taxYear = taxYear;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public int getTaxYear() {
        return taxYear;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public String getIrsTrackingId() {
        return irsTrackingId;
    }

    public Instant getAvailableAtEstimated() {
        return availableAtEstimated;
    }

    public void setAvailableAtEstimated(Instant availableAtEstimated) {
        this.availableAtEstimated = availableAtEstimated;
    }

    public void updateFromIrw(RefundStatus status, BigDecimal expectedAmount, String irsTrackingId) {
        this.status = status;
        this.expectedAmount = expectedAmount;
        this.irsTrackingId = irsTrackingId;
        this.lastUpdatedAt = Instant.now();
    }
}
