package com.intuit.taxrefundstatus.ai.model;

import com.intuit.taxrefundstatus.ai.AiConfig;
import com.intuit.taxrefundstatus.auth.model.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "ai_request_log",
    indexes = @Index(name = "ix_ai_user_created", columnList = "user_id, created_at")
)
public class AiRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, name = "request_type", length = 40)
    private String requestType;

    @Column(nullable = false, name = "input_payload", columnDefinition = "text")
    private String inputPayload;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "output_payload", columnDefinition = "text")
    private String outputPayload;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    private Boolean helpful;

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getInputPayload() {
        return inputPayload;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutputPayload() {
        return outputPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(Boolean helpful) {
        this.helpful = helpful;
    }

    protected AiRequestLog() {}

    public static AiRequestLog success(
        AppUser user, String requestType, String inputPayload,
        String provider, String model, String outputPayload) {
        return log(user, requestType, inputPayload, provider, model, outputPayload, true);
    }

    public static AiRequestLog failure(
        AppUser user, String requestType, String inputPayload,
        String provider, String model, String outputPayload) {
        return log(user, requestType, inputPayload, provider, model, outputPayload, false);
    }

    private static AiRequestLog log(
        AppUser user, String requestType, String inputPayload,
        String provider, String model, String outputPayload, boolean success) {
        AiRequestLog l = new AiRequestLog();
        l.user = user;
        l.requestType = requestType;
        l.inputPayload = inputPayload;
        l.provider = provider;
        l.model = model;
        l.success = success;
        l.outputPayload = outputPayload;
        return l;
    }

}
