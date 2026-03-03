package com.intuit.taxrefund.assistant.model;

import org.springframework.stereotype.Component;

/**
 * Finite-state-machine planner for the assistant conversation.
 *
 * Improvements over the original:
 *
 * 1. ESCALATE is now a live state.
 *    After {@link ConversationContext#ESCALATION_THRESHOLD} consecutive turns in
 *    TROUBLESHOOTING, or that many consecutive low-confidence turns, the planner
 *    transitions to ESCALATE and sets {@code plan.escalate = true}.
 *
 * 2. Bidirectional transitions.
 *    A clear REFUND_STATUS intent now resets the state from TROUBLESHOOTING back
 *    to PROVIDED_STATUS instead of being overridden by the fallback heuristic.
 *
 * 3. START no longer floods the LLM with ETA/policy data.
 *    On the very first turn we only fetch status – extra context is added once
 *    the user has actually asked a question.
 *
 * 4. Frustration awareness.
 *    Low-confidence turns are counted by the caller (AssistantService) and passed
 *    in via the ConversationContext so the planner can escalate independently of
 *    the FSM state path.
 */
@Component
public class AssistantPlanner {

    private static final double MIN_CONFIDENCE = 0.55;

    /**
     * Produce a plan for the current turn.
     *
     * @param ctx          full conversation context (state + counters + history)
     * @param intent       classifier result
     * @param confidence   classifier confidence score
     * @param refundStatus current IRS refund status string
     */
    public AssistantPlan plan(
        ConversationContext ctx,
        AssistantIntent intent,
        double confidence,
        String refundStatus
    ) {
        ConversationContext safeCtx = (ctx == null) ? ConversationContext.start() : ctx;
        ConversationState currentState = safeCtx.state();

        boolean isAvailable              = "AVAILABLE".equals(refundStatus);
        boolean isRejected               = "REJECTED".equals(refundStatus);
        boolean needsEtaOrTroubleshooting = !isAvailable && !isRejected;

        boolean isLowConf = (intent == null)
            || intent == AssistantIntent.UNKNOWN
            || confidence < MIN_CONFIDENCE;

        AssistantIntent effectiveIntent = (intent == null) ? AssistantIntent.UNKNOWN : intent;

        // ── Escalation check (highest priority) ─────────────────────────────
        // We check BEFORE resolving the effective intent so that even a correctly
        // classified turn can trigger escalation when the user is clearly stuck.
        if (safeCtx.shouldEscalate() || currentState == ConversationState.ESCALATE) {
            return new AssistantPlan(
                ConversationState.ESCALATE,
                true,
                false,
                false,
                true   // escalate = true → caller must surface CONTACT_SUPPORT
            );
        }

        // ── Confidence fallback ──────────────────────────────────────────────
        if (isLowConf) {
            effectiveIntent = fallbackIntentFromState(currentState, effectiveIntent);
        }

        // ── Intent → next state routing ──────────────────────────────────────
        return switch (effectiveIntent) {

            case REFUND_STATUS ->
                // Always honour an explicit status request, even from TROUBLESHOOTING
                // (bidirectional transition – fixes the original one-way bug).
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

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * When the classifier is uncertain, infer a reasonable intent from where we
     * currently are in the conversation rather than bailing to UNKNOWN.
     */
    private AssistantIntent fallbackIntentFromState(
        ConversationState state,
        AssistantIntent originalIntent
    ) {
        ConversationState s = (state == null) ? ConversationState.START : state;

        return switch (s) {
            case TROUBLESHOOTING ->
                // Stay in the troubleshooting flow unless the user explicitly asked for status
                (originalIntent == AssistantIntent.REFUND_STATUS)
                    ? AssistantIntent.REFUND_STATUS
                    : AssistantIntent.WHY_DELAYED;

            case PROVIDED_ETA -> {
                if (originalIntent == AssistantIntent.REFUND_STATUS) yield AssistantIntent.REFUND_STATUS;
                yield AssistantIntent.REFUND_ETA;
            }

            case PROVIDED_STATUS ->
                AssistantIntent.REFUND_STATUS;

            case START -> {
                // On the first turn, honour whatever the classifier found (even low-conf)
                // rather than defaulting to status; yield status only if truly unknown.
                if (originalIntent != null && originalIntent != AssistantIntent.UNKNOWN) {
                    yield originalIntent;
                }
                yield AssistantIntent.REFUND_STATUS;
            }

            // ESCALATE is handled before we reach this method; catch-all is safe.
            default -> AssistantIntent.REFUND_STATUS;
        };
    }

    /**
     * When no intent was resolvable at all, maintain the current state's context
     * rather than silently resetting to START.
     *
     * Key fix vs original: START no longer injects ETA/policy data (noisy for a
     * greeting or first message before the user has asked anything).
     */
    private AssistantPlan defaultPlanForState(
        ConversationState state,
        boolean needsEtaOrTroubleshooting
    ) {
        ConversationState s = (state == null) ? ConversationState.START : state;

        return switch (s) {
            case PROVIDED_ETA ->
                new AssistantPlan(ConversationState.PROVIDED_ETA, true, needsEtaOrTroubleshooting, true);

            case TROUBLESHOOTING ->
                new AssistantPlan(ConversationState.TROUBLESHOOTING, true, needsEtaOrTroubleshooting, true);

            case PROVIDED_STATUS ->
                new AssistantPlan(ConversationState.PROVIDED_STATUS, true, false, false);

            // START: only fetch status on the first greeting – don't overwhelm the LLM.
            case START ->
                new AssistantPlan(ConversationState.START, true, false, false);

            default ->
                new AssistantPlan(ConversationState.START, true, false, false);
        };
    }
}
