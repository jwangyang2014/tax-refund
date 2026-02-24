package com.intuit.taxrefund.refund.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundStatusResponse(
    int taxYear,
    String status,
    Instant lastUpdatedAt,
    BigDecimal expectedAmount,
    String trackingId,
    Instant availableAtEstimated,
    String aiExplanation
) {}
