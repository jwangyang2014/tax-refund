package com.intuit.taxrefund.refund.integration.irs;

import com.intuit.taxrefund.refund.model.RefundStatus;

import java.math.BigDecimal;

public interface IrsAdapter {
    IrsRefundResult fetchMostRecentRefund(Long userId);

    record IrsRefundResult(
        int taxYear,
        RefundStatus status,
        BigDecimal expectedAmount,
        String trackingId
    ) {}
}
