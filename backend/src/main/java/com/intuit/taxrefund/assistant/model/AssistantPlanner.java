package com.intuit.taxrefund.assistant.model;

import org.springframework.stereotype.Component;

@Component
public class AssistantPlanner {

    private static final double MIN_CONFIDENCE = 0.55;

    public AssistantPlan plan(ConversationState state, AssistantIntent intent, double confidence, String refundStatus) {
        ConversationState currentState = (state == null) ? ConversationState.START : state;

        boolean isAvailable = "AVAILABLE".equals(refundStatus);
        boolean isRejected  = "REJECTED".equals(refundStatus);
        boolean needsEtaOrTroubleshooting = !isAvailable && !isRejected;

        AssistantIntent effectiveIntent = (intent == null) ? AssistantIntent.UNKNOWN : intent;

        // If classifier confidence is low, fallback to previous conversation state
        if (effectiveIntent == AssistantIntent.UNKNOWN || confidence < MIN_CONFIDENCE) {
            effectiveIntent = fallbackIntentFromState(currentState, effectiveIntent);
        }

        return switch (effectiveIntent) {
            case REFUND_STATUS ->
                new AssistantPlan(ConversationState.PROVIDED_STATUS, true, false, false);

            case REFUND_ETA ->
                new AssistantPlan(ConversationState.PROVIDED_ETA, true, needsEtaOrTroubleshooting, true);

            case WHY_DELAYED, NEXT_STEPS ->
                new AssistantPlan(ConversationState.TROUBLESHOOTING, true, needsEtaOrTroubleshooting, true);

            case UNKNOWN ->
                defaultPlanForState(currentState, needsEtaOrTroubleshooting);

            default ->
                defaultPlanForState(currentState, needsEtaOrTroubleshooting);
        };
    }

    private AssistantIntent fallbackIntentFromState(ConversationState state, AssistantIntent originalIntent) {
        ConversationState s = (state == null) ? ConversationState.START : state;

        return switch (s) {
            case TROUBLESHOOTING -> AssistantIntent.WHY_DELAYED;

            case PROVIDED_ETA -> {
                if (originalIntent == AssistantIntent.REFUND_STATUS) yield AssistantIntent.REFUND_STATUS;
                yield AssistantIntent.REFUND_ETA;
            }

            case PROVIDED_STATUS -> AssistantIntent.REFUND_STATUS;

            case START -> {
                if (originalIntent != null && originalIntent != AssistantIntent.UNKNOWN) yield originalIntent;
                yield AssistantIntent.REFUND_STATUS;
            }

            default -> AssistantIntent.REFUND_STATUS;
        };
    }

    private AssistantPlan defaultPlanForState(ConversationState state, boolean needsEtaOrTroubleshooting) {
        ConversationState s = (state == null) ? ConversationState.START : state;

        return switch (s) {
            case PROVIDED_ETA ->
                new AssistantPlan(ConversationState.PROVIDED_ETA, true, needsEtaOrTroubleshooting, true);

            case TROUBLESHOOTING ->
                new AssistantPlan(ConversationState.TROUBLESHOOTING, true, needsEtaOrTroubleshooting, true);

            case PROVIDED_STATUS ->
                new AssistantPlan(ConversationState.PROVIDED_STATUS, true, false, false);

            case START ->
                new AssistantPlan(ConversationState.START, true, needsEtaOrTroubleshooting, true);

            default ->
                new AssistantPlan(ConversationState.START, true, needsEtaOrTroubleshooting, true);
        };
    }
}