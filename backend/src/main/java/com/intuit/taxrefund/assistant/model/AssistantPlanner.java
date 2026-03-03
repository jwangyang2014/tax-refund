package com.intuit.taxrefund.assistant.model;

import org.springframework.stereotype.Component;

/**
 * FSM planner – v3.
 *
 * Fixes three bugs from v2:
 *
 * ── Bug 1: Off-topic messages silently hijacked by fallback ──────────────────
 * Previously, any UNKNOWN/low-confidence message was fed into fallbackFromState()
 * which fabricated a refund-domain intent.  A user saying "what's the weather?"
 * would receive a refund status answer.
 *
 * Fix: two-level confidence check.
 *   • confidence < HARD_FLOOR (0.35)  AND  intent == UNKNOWN
 *     → genuinely off-topic.  Return AssistantPlan.offTopic() so AssistantService
 *       responds with a polite clarification and touches no state.
 *   • confidence in [HARD_FLOOR, MIN_CONFIDENCE)  OR  intent recognised but weak
 *     → uncertain but in-domain.  Use fallbackFromState() as before, stateGated=true.
 *
 * ── Bug 2: Escalating transitions fire on borderline confidence ───────────────
 * Moving from a "happy" state (PROVIDED_STATUS, PROVIDED_ETA) into TROUBLESHOOTING
 * previously required only MIN_CONFIDENCE (0.55).  That's too low — a 0.57-confidence
 * WHY_DELAYED on "ok thanks" was enough to send the user into troubleshooting.
 *
 * Fix: ESCALATING_CONFIDENCE (0.75).  Transitions that move the FSM *forward into
 * a worse state* (provided→troubleshooting) require the classifier to be highly
 * confident.  Below that threshold the FSM stays in the current state and the
 * weaker intent is still answered (stateGated=true) so the user gets a response.
 *
 * ── Bug 3: PROVIDED_ETA unconditionally advanced on any WHY_DELAYED ──────────
 * Covered by Bug 2 fix above.  In addition, PROVIDED_ETA is now treated as a
 * stable state: the only transitions *out* of it are explicit high-confidence
 * WHY_DELAYED/NEXT_STEPS (→ TROUBLESHOOTING) or REFUND_STATUS (→ PROVIDED_STATUS).
 * Anything else stays at PROVIDED_ETA with stateGated=true.
 */
@Component
public class AssistantPlanner {

    /**
     * Below this the message is considered off-topic when intent is also UNKNOWN.
     * No refund-domain fallback is applied; the service returns a clarification.
     */
    private static final double HARD_FLOOR = 0.35;

    /**
     * Minimum confidence for any state change.  Between HARD_FLOOR and this
     * value the intent is used for answering but confirmedState does not advance.
     */
    private static final double MIN_CONFIDENCE = 0.55;

    /**
     * Minimum confidence required for *escalating* transitions — those that move
     * the FSM from a resolved/happy state into a worse one (e.g. PROVIDED_ETA →
     * TROUBLESHOOTING).  Prevents borderline classifier hits from prematurely
     * pulling the user into a problem flow they didn't express.
     */
    private static final double ESCALATING_CONFIDENCE = 0.75;

    // ── Public API ────────────────────────────────────────────────────────────

    public AssistantPlan plan(ConversationContext ctx, AssistantIntent intent,
                              double confidence, String refundStatus) {

        ConversationContext safeCtx = (ctx == null) ? ConversationContext.start() : ctx;
        ConversationState   cur     = safeCtx.state();
        ConversationState   confirmed = safeCtx.confirmedState();

        boolean isAvailable  = "AVAILABLE".equals(refundStatus);
        boolean isRejected   = "REJECTED".equals(refundStatus);
        boolean needsEtaOrTs = !isAvailable && !isRejected;

        AssistantIntent eff = (intent == null) ? AssistantIntent.UNKNOWN : intent;

        // ── 1. Escalation – always highest priority ───────────────────────────
        if (safeCtx.shouldEscalate() || cur == ConversationState.ESCALATE) {
            return new AssistantPlan(ConversationState.ESCALATE, true, false, false, true);
        }

        // ── 2. Off-topic detection ────────────────────────────────────────────
        // A message is off-topic when:
        //   (a) the classifier has no recognisable intent (UNKNOWN), AND
        //   (b) confidence is below the hard floor (so even "uncertain refund" passes)
        // We stay at confirmedState and ask the user to rephrase.
        boolean trulyUnknown  = (eff == AssistantIntent.UNKNOWN);
        boolean belowHardFloor = confidence < HARD_FLOOR;

        if (trulyUnknown && belowHardFloor) {
            return AssistantPlan.offTopic(confirmed);
        }

        // ── 3. Confidence tier classification ─────────────────────────────────
        //
        //  HIGH  : confidence >= MIN_CONFIDENCE  → use intent directly
        //  WEAK  : HARD_FLOOR <= confidence < MIN_CONFIDENCE, or UNKNOWN with
        //          confidence >= HARD_FLOOR (in-domain but uncertain)
        //          → fallback to current state's expected intent, stateGated=true
        boolean isWeakSignal = trulyUnknown || confidence < MIN_CONFIDENCE;

        if (isWeakSignal) {
            // Uncertain but in-domain: infer intent from current state.
            // State does not advance (stateGated) but the user still gets a reply.
            eff = fallbackFromState(cur, eff);
            AssistantPlan base = route(eff, cur, confirmed, needsEtaOrTs, confidence);
            return new AssistantPlan(
                base.nextState(),
                base.includeRefundStatus(), base.includeEta(), base.includePolicySnippets(),
                false,
                safeCtx.isRepeating(),  // repeatHint
                true,                   // stateGated – weak signal, don't advance
                false);
        }

        // ── 4. Gate signals from previous-turn outcome ────────────────────────
        boolean isRepeating = safeCtx.isRepeating();
        boolean lastLow     = safeCtx.lastAnswerWasLow();
        boolean gate        = isRepeating || lastLow;

        // ── 5. Route high-confidence intent to next state ─────────────────────
        AssistantPlan base = route(eff, cur, confirmed, needsEtaOrTs, confidence);

        if (gate) {
            return new AssistantPlan(
                base.nextState(),
                base.includeRefundStatus(), base.includeEta(), base.includePolicySnippets(),
                false,
                isRepeating,
                true,   // stateGated
                false);
        }

        return base;
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /**
     * Maps a high-confidence (or fallback-resolved) intent to a plan.
     *
     * Escalating transitions (happy → troubled) require ESCALATING_CONFIDENCE.
     * If the confidence is high enough to classify the intent but not high enough
     * for an escalating transition, we stay in the current state (stateGated) and
     * still answer the softer version of the question.
     *
     * @param confidence passed through so routing can apply the escalating threshold
     */
    private AssistantPlan route(AssistantIntent eff, ConversationState cur,
                                ConversationState confirmed,
                                boolean needsEtaOrTs, double confidence) {
        return switch (eff) {

            case REFUND_STATUS ->
                // Always honour – moving to PROVIDED_STATUS is never "escalating"
                new AssistantPlan(ConversationState.PROVIDED_STATUS, true, false, false);

            case REFUND_ETA ->
                new AssistantPlan(ConversationState.PROVIDED_ETA, true, needsEtaOrTs, true);

            case WHY_DELAYED, NEXT_STEPS -> {
                // Escalating transition: only move FSM forward if classifier is
                // confident enough.  Otherwise stay in current state but still
                // answer the question (stateGated so confirmedState holds).
                boolean isEscalatingFrom = isHappyState(cur);
                if (isEscalatingFrom && confidence < ESCALATING_CONFIDENCE) {
                    // Answer the troubleshooting question but don't advance state
                    yield new AssistantPlan(
                        cur,                    // stay in current state
                        true, needsEtaOrTs, true,
                        false, false,
                        true,                   // stateGated
                        false);
                }
                yield new AssistantPlan(ConversationState.TROUBLESHOOTING, true, needsEtaOrTs, true);
            }

            case OFF_TOPIC ->
                // Shouldn't reach here (handled above), but safe fallback
                AssistantPlan.offTopic(confirmed);

            default ->
                defaultForState(cur, needsEtaOrTs);
        };
    }

    /**
     * Happy states are those where the user's need was met without problems.
     * Transitioning *out* of these into TROUBLESHOOTING is an escalating move
     * and requires higher classifier confidence.
     */
    private static boolean isHappyState(ConversationState s) {
        return s == ConversationState.PROVIDED_STATUS
            || s == ConversationState.PROVIDED_ETA;
    }

    /**
     * When the NLP signal is weak but in-domain, infer the most likely intent
     * from where the conversation currently is.  This prevents the FSM from
     * drifting to an unrelated state on an ambiguous message.
     */
    private AssistantIntent fallbackFromState(ConversationState s, AssistantIntent orig) {
        if (s == null) s = ConversationState.START;
        return switch (s) {

            case TROUBLESHOOTING ->
                // Allow explicit status exit; otherwise keep troubleshooting
                (orig == AssistantIntent.REFUND_STATUS)
                    ? AssistantIntent.REFUND_STATUS
                    : AssistantIntent.WHY_DELAYED;

            case PROVIDED_ETA -> {
                // Stay on ETA topic unless user clearly asked for plain status
                if (orig == AssistantIntent.REFUND_STATUS) yield AssistantIntent.REFUND_STATUS;
                yield AssistantIntent.REFUND_ETA;
            }

            case PROVIDED_STATUS ->
                AssistantIntent.REFUND_STATUS;

            case START -> {
                // On the first turn honour whatever partial signal exists
                if (orig != null && orig != AssistantIntent.UNKNOWN) yield orig;
                yield AssistantIntent.REFUND_STATUS;
            }

            // ESCALATE handled before this method is reached
            default -> AssistantIntent.REFUND_STATUS;
        };
    }

    /**
     * When intent resolution produces UNKNOWN (after fallback), maintain the
     * current state's context.  START no longer injects ETA/policy noise.
     */
    private AssistantPlan defaultForState(ConversationState s, boolean needsEtaOrTs) {
        if (s == null) s = ConversationState.START;
        return switch (s) {
            case PROVIDED_ETA ->
                new AssistantPlan(ConversationState.PROVIDED_ETA, true, needsEtaOrTs, true);
            case TROUBLESHOOTING ->
                new AssistantPlan(ConversationState.TROUBLESHOOTING, true, needsEtaOrTs, true);
            case PROVIDED_STATUS ->
                new AssistantPlan(ConversationState.PROVIDED_STATUS, true, false, false);
            default ->
                new AssistantPlan(ConversationState.START, true, false, false);
        };
    }
}
