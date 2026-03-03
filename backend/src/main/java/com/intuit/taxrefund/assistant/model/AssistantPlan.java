package com.intuit.taxrefund.assistant.model;

/**
 * Immutable planning decision produced by {@link AssistantPlanner} for one turn.
 *
 * Flags (additive – each solves one specific problem):
 *
 *   escalate    – user is stuck; surface CONTACT_SUPPORT, tell LLM to hand off
 *   repeatHint  – same intent seen N times; tell LLM to try a different angle
 *   stateGated  – do NOT commit nextState as confirmedState this turn
 *   offTopic    – user's message has no recognisable refund-domain intent;
 *                 AssistantService must return a clarification response directly
 *                 without calling the LLM and without changing any state
 */
public record AssistantPlan(
    ConversationState nextState,
    boolean includeRefundStatus,
    boolean includeEta,
    boolean includePolicySnippets,
    boolean escalate,
    boolean repeatHint,
    boolean stateGated,
    boolean offTopic
) {
    // ── Convenience constructors (most callers don't need every flag) ─────────

    /** Normal plan – no special flags. */
    public AssistantPlan(ConversationState nextState,
                         boolean includeRefundStatus,
                         boolean includeEta,
                         boolean includePolicySnippets) {
        this(nextState, includeRefundStatus, includeEta, includePolicySnippets,
            false, false, false, false);
    }

    /** Escalation plan. */
    public AssistantPlan(ConversationState nextState,
                         boolean includeRefundStatus,
                         boolean includeEta,
                         boolean includePolicySnippets,
                         boolean escalate) {
        this(nextState, includeRefundStatus, includeEta, includePolicySnippets,
            escalate, false, false, false);
    }

    /** Gated / repeat plan. */
    public AssistantPlan(ConversationState nextState,
                         boolean includeRefundStatus,
                         boolean includeEta,
                         boolean includePolicySnippets,
                         boolean escalate,
                         boolean repeatHint,
                         boolean stateGated) {
        this(nextState, includeRefundStatus, includeEta, includePolicySnippets,
            escalate, repeatHint, stateGated, false);
    }

    // ── Static factories for clarity at call sites ────────────────────────────

    /** Off-topic plan: stay in confirmedState, no LLM call. */
    public static AssistantPlan offTopic(ConversationState confirmedState) {
        return new AssistantPlan(confirmedState,
            false, false, false,    // no data to fetch
            false, false,
            true,          // stateGated – confirmedState stays put
            true);                  // offTopic   – service returns clarification
    }
}
