package com.intuit.taxrefund.refund.controller.dto;

import com.intuit.taxrefund.refund.model.RefundStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RefundStatusInternalUpdateRequest(
    int taxYear,
    @NotBlank String status,
    @NotNull BigDecimal expectedAmount,
    @NotBlank String trackingId
) {
    public RefundStatus statusEnum() {
        return RefundStatus.valueOf(status);
    }
}
