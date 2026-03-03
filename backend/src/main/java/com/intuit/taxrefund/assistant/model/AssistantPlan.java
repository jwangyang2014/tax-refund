package com.intuit.taxrefund.assistant.model;

/**
 * Immutable planning decision produced by {@link AssistantPlanner} for a single turn.
 *
 * {@code escalate} – when true the service should surface a
 * CONTACT_SUPPORT action and tell the LLM the user needs a human agent.
 */
public record AssistantPlan(
    ConversationState nextState,
    boolean includeRefundStatus,
    boolean includeEta,
    boolean includePolicySnippets,
    boolean escalate
) {
    /** Convenience constructor for non-escalation plans (backward-compatible). */
    public AssistantPlan(
        ConversationState nextState,
        boolean includeRefundStatus,
        boolean includeEta,
        boolean includePolicySnippets
    ) {
        this(nextState, includeRefundStatus, includeEta, includePolicySnippets, false);
    }
}
