package com.intuit.taxrefund.refund.integration.eta;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
    name = "refund_eta_prediction",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_eta_user_year_status_model",
        columnNames = {"user_id", "tax_year", "status", "model_version"}
    ),
    indexes = {
        @Index(name = "ix_eta_user_year_time", columnList = "user_id,tax_year,created_at")
    }
)
public class RefundEtaPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_id")
    private Long userId;

    @Column(nullable = false, name = "tax_year")
    private int taxYear;

    @Column(nullable = false, length = 40)
    private String status; // store enum name as string for simplicity

    @Column(nullable = false, name = "eta_days")
    private int etaDays;

    @Column(name = "estimated_available_at")
    private Instant estimatedAvailableAt;

    @Column(nullable = false, name = "model_name", length = 120)
    private String modelName;

    @Column(nullable = false, name = "model_version", length = 120)
    private String modelVersion;

    // keep demo-simple: store JSON as string
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String features;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    protected RefundEtaPrediction() {}

    public RefundEtaPrediction(
        Long userId,
        int taxYear,
        String status,
        int etaDays,
        Instant estimatedAvailableAt,
        String modelName,
        String modelVersion,
        String features
    ) {
        this.userId = userId;
        this.taxYear = taxYear;
        this.status = status;
        this.etaDays = etaDays;
        this.estimatedAvailableAt = estimatedAvailableAt;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.features = features;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getTaxYear() { return taxYear; }
    public String getStatus() { return status; }
    public int getEtaDays() { return etaDays; }
    public Instant getEstimatedAvailableAt() { return estimatedAvailableAt; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public String getFeatures() { return features; }
    public Instant getCreatedAt() { return createdAt; }
}