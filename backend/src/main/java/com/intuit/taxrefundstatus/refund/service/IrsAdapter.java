package com.intuit.taxrefundstatus.refund.service;

import com.intuit.taxrefundstatus.refund.model.RefundStatus;

import java.math.BigDecimal;

public interface IrsAdapter {
    IrsRefundResult fetchMostRecentRefund(Long userId);

    record IrsRefundResult(
        int taxYear,
        RefundStatus refundStatus,
        BigDecimal expectedAmount,
        String trackingId
    ) {}
}
