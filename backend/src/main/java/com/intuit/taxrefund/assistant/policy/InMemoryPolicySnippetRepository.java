package com.intuit.taxrefund.assistant.policy;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public class InMemoryPolicySnippetRepository implements PolicySnippetRepository {

    @Override
    public List<PolicySnippetRecord> findCandidates(String locale) {
        return List.of(
            new PolicySnippetRecord(
                "IRS_REFUND_GENERAL_001",
                "Peak processing delays",
                "Refund status updates can be delayed during peak processing periods.",
                "IRS",
                "https://www.irs.gov/refunds",
                "IRS Refunds",
                "2026.01",
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                Set.of("*"),
                "en-US",
                true,
                10
            ),
            new PolicySnippetRecord(
                "IRS_REFUND_BANK_POSTING_001",
                "Bank posting delays",
                "If a refund is marked SENT, banks may require additional time to post deposits.",
                "IRS",
                "https://www.irs.gov/refunds",
                "IRS Refunds",
                "2026.01",
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                Set.of("SENT", "AVAILABLE"),
                "en-US",
                true,
                20
            ),
            new PolicySnippetRecord(
                "IRS_REFUND_PROCESSING_001",
                "Processing review delays",
                "Long processing times can occur due to verification or return review.",
                "IRS",
                "https://www.irs.gov/refunds",
                "IRS Refunds",
                "2026.01",
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                Set.of("PROCESSING"),
                "en-US",
                true,
                5
            ),
            new PolicySnippetRecord(
                "IRS_REFUND_REJECTED_001",
                "Rejected refund next steps",
                "Rejected refunds often require correcting filing details or addressing notices.",
                "IRS",
                "https://www.irs.gov/refunds",
                "IRS Refunds",
                "2026.01",
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                Set.of("REJECTED"),
                "en-US",
                true,
                5
            )
        );
    }
}