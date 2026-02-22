package com.intuit.taxrefund.assistant.core;

import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PrivacyFilter {
    /**
     * Authoritative data allowed to be sent to external LLM providers.
     * DO NOT include: trackingId, userId, names, addresses, SSN, bank info, exact amounts (optional).
     */
    public Map<String, Object> buildAuthoritativeDataForLlm(RefundStatusResponse refund) {
        Map<String, Object> refundSafe = new LinkedHashMap<>();
        refundSafe.put("taxYear", refund.taxYear());
        refundSafe.put("status", refund.status());
        refundSafe.put("lastUpdatedAt", refund.lastUpdatedAt());
        refundSafe.put("expectedAmountBucket", bucketAmount(refund.expectedAmount())); // bucket, not exact
        // intentionally omit trackingId

        Map<String, Object> etaSafe = new LinkedHashMap<>();
        etaSafe.put("estimatedAvailableAt", refund.availableAtEstimated());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("refund", refundSafe);
        root.put("eta", etaSafe);
        return root;
    }

    private static String bucketAmount(BigDecimal amt) {
        if (amt == null) return "unknown";
        double a = amt.doubleValue();
        if (a < 500) return "<$500";
        if (a < 1000) return "$500-$1k";
        if (a < 2000) return "$1k-$2k";
        if (a < 5000) return "$2k-$5k";
        if (a < 10000) return "$5k-$10k";
        return ">$10k";
    }
}