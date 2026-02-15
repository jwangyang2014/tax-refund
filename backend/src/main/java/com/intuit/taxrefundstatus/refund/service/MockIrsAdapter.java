package com.intuit.taxrefundstatus.refund.service;

import com.intuit.taxrefundstatus.refund.model.RefundStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockIrsAdapter implements IrsAdapter {
    private final Map<Long, IrsRefundResult> store = new ConcurrentHashMap<>();

    @Override
    public IrsRefundResult fetchMostRecentRefund(Long userId) {
        return store.getOrDefault(userId, new IrsRefundResult(
            2025,
            RefundStatus.RECEIVED, new BigDecimal(1234.56),
            "MOCK-" + userId
        ));
    }

    public void upsert(Long userId, IrsRefundResult result) {
        store.put(userId, result);
    }
}
