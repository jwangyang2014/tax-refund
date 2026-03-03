package com.intuit.taxrefund.assistant.infra;

import com.intuit.taxrefund.assistant.model.AssistantPlan;
import com.intuit.taxrefund.refund.controller.dto.RefundStatusResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PrivacyFilter {
    /**
     * Build authoritative data allowed to be sent to external LLM providers.
     *
     * Planner flags decide which sections are included:
     * - includeRefundStatus -> include refund status metadata
     * - includeEta          -> include ETA metadata
     *
     * Privacy rules:
     * - DO NOT include trackingId, userId, names, addresses, SSN, bank info
     * - Use amount bucket instead of exact amount
     */
    public Map<String, Object> buildAuthoritativeDataForLlm(
        RefundStatusResponse refund,
        AssistantPlan plan
    ) {
        Map<String, Object> root = new LinkedHashMap<>();

        if (plan.includeRefundStatus()) {
            Map<String, Object> refundSafe = new LinkedHashMap<>();
            refundSafe.put("taxYear", refund.taxYear());
            refundSafe.put("status", refund.status());
            refundSafe.put("lastUpdatedAt", refund.lastUpdatedAt());
            refundSafe.put("expectedAmountBucket", bucketAmount(refund.expectedAmount()));
            root.put("refund", refundSafe);
        }

        if (plan.includeEta()) {
            Map<String, Object> etaSafe = new LinkedHashMap<>();
            etaSafe.put("estimatedAvailableAt", refund.availableAtEstimated());
            root.put("eta", etaSafe);
        }

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