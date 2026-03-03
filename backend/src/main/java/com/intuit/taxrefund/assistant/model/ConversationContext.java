package com.intuit.taxrefund.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Rich session context stored in Redis per user.
 *
 * Replaces the bare ConversationState string with a structured object that
 * tracks FSM state, frustration signals, and a short conversation history
 * so the LLM can produce contextually coherent multi-turn replies.
 *
 * Fields
 * ──────
 * state                – current FSM state
 * turnCount            – total turns in this session
 * troubleshootingTurns – consecutive turns spent in TROUBLESHOOTING (escalation signal)
 * lowConfidenceTurns   – consecutive turns where classifier confidence < threshold
 * history              – last N (Q, A) pairs for LLM prompt injection
 */
public record ConversationContext(
    ConversationState state,
    int turnCount,
    int troubleshootingTurns,
    int lowConfidenceTurns,
    List<HistoryEntry> history
) {

    public static final int MAX_HISTORY          = 5;
    public static final int ESCALATION_THRESHOLD = 3;

    /** Blank starting context for a brand-new session. */
    public static ConversationContext start() {
        return new ConversationContext(ConversationState.START, 0, 0, 0, List.of());
    }

    /**
     * Returns a new context after a completed turn.
     *
     * @param nextState    FSM state chosen by AssistantPlanner
     * @param wasLowConf   true when classifier confidence was below MIN_CONFIDENCE
     * @param userQuestion raw user message
     * @param botAnswer    the assistant reply markdown
     */
    public ConversationContext advance(
        ConversationState nextState,
        boolean wasLowConf,
        String userQuestion,
        String botAnswer
    ) {
        int newTurnCount = turnCount + 1;

        // Reset counter whenever we leave TROUBLESHOOTING; increment while we stay.
        int newTroubleshootingTurns = (nextState == ConversationState.TROUBLESHOOTING)
            ? troubleshootingTurns + 1
            : 0;

        // Reset on any confident turn; increment on consecutive low-confidence turns.
        int newLowConfTurns = wasLowConf ? lowConfidenceTurns + 1 : 0;

        // Sliding window – keep only the most recent MAX_HISTORY entries.
        List<HistoryEntry> newHistory = new ArrayList<>(history);
        newHistory.add(new HistoryEntry(userQuestion, botAnswer));
        if (newHistory.size() > MAX_HISTORY) {
            newHistory = newHistory.subList(newHistory.size() - MAX_HISTORY, newHistory.size());
        }

        return new ConversationContext(
            nextState,
            newTurnCount,
            newTroubleshootingTurns,
            newLowConfTurns,
            List.copyOf(newHistory)
        );
    }

    /**
     * True when the user appears stuck and should be offered a human-agent escalation.
     * Triggered by either too many turns in TROUBLESHOOTING or too many consecutive
     * low-confidence (unrecognised intent) turns.
     */
    public boolean shouldEscalate() {
        return troubleshootingTurns >= ESCALATION_THRESHOLD
            || lowConfidenceTurns   >= ESCALATION_THRESHOLD;
    }

    /**
     * One (question, answer) pair in the sliding history window.
     * Serialised as JSON by ConversationStateStore.
     */
    public record HistoryEntry(String question, String answer) {}
}
