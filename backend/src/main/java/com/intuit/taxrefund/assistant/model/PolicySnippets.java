package com.intuit.taxrefund.assistant.model;

import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicySnippets {

    public List<Citation> forStatus(String refundStatus) {
        List<Citation> out = new ArrayList<>();
        out.add(new Citation("IRS_HELP_01", "Refund status updates can be delayed during peak processing periods."));
        out.add(new Citation("IRS_HELP_02", "If a refund is marked SENT, banks may require additional time to post deposits."));

        if ("PROCESSING".equals(refundStatus)) {
            out.add(new Citation("IRS_HELP_03", "Long processing times can occur due to verification or return review."));
        }
        if ("REJECTED".equals(refundStatus)) {
            out.add(new Citation("IRS_HELP_04", "Rejected refunds often require correcting filing details or addressing notices."));
        }
        return out;
    }
}