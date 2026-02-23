package com.intuit.taxrefund.assistant.model;

public record AssistantPlan(
    ConversationState nextState,
    boolean includeRefundStatus,
    boolean includeEta,
    boolean includePolicySnippets
) {}