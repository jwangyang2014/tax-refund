package com.intuit.taxrefund.assistant.model;

import org.springframework.stereotype.Component;

@Component
public class AssistantPlanner {

    public AssistantPlan plan(ConversationState state, AssistantIntent intent, String refundStatus) {
        boolean isAvailable = "AVAILABLE".equals(refundStatus);
        boolean isRejected  = "REJECTED".equals(refundStatus);

        return switch (intent) {
            case REFUND_STATUS ->
                new AssistantPlan(ConversationState.PROVIDED_STATUS, true, false, false);

            case REFUND_ETA ->
                new AssistantPlan(ConversationState.PROVIDED_ETA, true, !isAvailable && !isRejected, true);

            case WHY_DELAYED, NEXT_STEPS ->
                new AssistantPlan(ConversationState.TROUBLESHOOTING, true, !isAvailable && !isRejected, true);

            default ->
                new AssistantPlan(ConversationState.START, true, !isAvailable && !isRejected, true);
        };
    }
}