package com.intuit.taxrefund.assistant.policy;

import java.time.Instant;
import java.util.Set;

public record PolicySnippetRecord(
    String id,                 // stable citation id: IRS_REFUND_DELAY_001
    String title,              // "Refund processing delays"
    String quote,              // canonical approved text
    String sourceType,         // IRS | INTUIT_HELP | INTERNAL_POLICY
    String sourceUrl,          // optional official help URL
    String sourceTitle,        // e.g. "Where's My Refund?"
    String version,            // e.g. "2026.01"
    Instant effectiveFrom,
    Instant effectiveTo,       // nullable
    Set<String> statusTriggers, // PROCESSING, REJECTED, SENT, *
    String locale,             // en-US
    boolean enabled,
    int priority               // lower = higher priority
) {
    public boolean isActiveAt(Instant now) {
        if (!enabled) return false;
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) return false;
        if (effectiveTo != null && !now.isBefore(effectiveTo)) return false;
        return true;
    }

    public boolean matchesStatus(String refundStatus) {
        if (statusTriggers == null || statusTriggers.isEmpty()) return false;
        return statusTriggers.contains("*") || statusTriggers.contains(refundStatus);
    }

    public boolean matchesLocale(String requestedLocale) {
        if (locale == null || locale.isBlank()) return true;
        return locale.equalsIgnoreCase(requestedLocale);
    }
}