package com.intuit.taxrefund.assistant.model;

/**
 * Intents the NLP classifier can produce.
 *
 * OFF_TOPIC is new: it is NOT produced by the classifier itself (which only
 * knows about refund-domain labels).  It is assigned by AssistantPlanner when
 * the classifier returns UNKNOWN with a confidence score below the hard floor,
 * meaning the user's message has no recognisable connection to the refund domain.
 *
 * Keeping OFF_TOPIC here (rather than as a planner-internal flag) lets every
 * downstream component — AssistantPlan, AssistantService, logs — treat it
 * uniformly without special-casing null checks.
 */
public enum AssistantIntent {
    REFUND_STATUS,
    REFUND_ETA,
    WHY_DELAYED,
    NEXT_STEPS,
    UNKNOWN,
    OFF_TOPIC
}
