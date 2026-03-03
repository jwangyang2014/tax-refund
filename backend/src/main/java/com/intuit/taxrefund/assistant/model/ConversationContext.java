package com.intuit.taxrefund.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Rich session context stored in Redis per user.
 *
 * v2 additions (disconnect fix):
 * ──────────────────────────────
 * pendingIntent       – the intent the bot attempted to answer last turn.
 *                       Used to detect when the user repeats themselves, which
 *                       signals the previous answer was unsatisfactory.
 *
 * repeatCount         – how many consecutive turns have had the same classified
 *                       intent.  Resets to 0 whenever the intent changes.
 *                       When this reaches REPEAT_THRESHOLD the planner tells
 *                       the LLM to try a different approach and escalates shortly
 *                       after if the user is still stuck.
 *
 * lastAnswerConfidence – the LLM's own self-reported confidence from the previous
 *                        turn (LOW / MEDIUM / HIGH as a string).  The planner
 *                        uses this to gate whether state actually advances:
 *                        a LOW-confidence answer must not move the FSM forward.
 *
 * confirmedState      – the last state that was reached with a satisfactory answer
 *                       (MEDIUM or HIGH confidence, non-repeated intent).
 *                       When the planner decides NOT to advance, the FSM returns
 *                       to confirmedState rather than staying in a "half-moved" state.
 */
public record ConversationContext(
    ConversationState state,
    ConversationState confirmedState,       // last state that was properly served
    AssistantIntent   pendingIntent,        // intent attempted in the previous turn
    int               repeatCount,          // consecutive turns with same intent
    int               turnCount,
    int               troubleshootingTurns,
    int               lowConfidenceTurns,
    String            lastAnswerConfidence, // "LOW" | "MEDIUM" | "HIGH" | null
    List<HistoryEntry> history
) {

    public static final int MAX_HISTORY          = 5;
    public static final int ESCALATION_THRESHOLD = 3;
    public static final int REPEAT_THRESHOLD     = 2;  // same intent N times → try differently

    // ── Factories ────────────────────────────────────────────────────────────

    /** Blank starting context for a brand-new session. */
    public static ConversationContext start() {
        return new ConversationContext(
            ConversationState.START,
            ConversationState.START,
            null,   // no pending intent yet
            0,
            0,
            0,
            0,
            null,
            List.of()
        );
    }

    // ── Advance ──────────────────────────────────────────────────────────────

    /**
     * Produces a new context after a completed turn.
     *
     * The key decision here is whether to actually commit {@code proposedNextState}
     * or roll back to {@code confirmedState}:
     *
     *   - If the LLM answered with LOW confidence → do NOT advance the confirmed
     *     state.  The user's need was not served; keep the FSM where it was.
     *   - If the same intent was repeated → also don't advance (the previous
     *     answer failed, we're retrying).
     *   - Otherwise → commit the new state as the confirmed state.
     *
     * @param proposedNextState  state chosen by AssistantPlanner for this turn
     * @param classifiedIntent   intent the classifier found (may be null/UNKNOWN)
     * @param wasLowNlpConf      true when NLP classifier confidence < threshold
     * @param llmAnswerConfidence the LLM's self-reported confidence ("LOW"/"MEDIUM"/"HIGH")
     * @param userQuestion       raw user message
     * @param botAnswer          assistant reply markdown
     */
    public ConversationContext advance(
        ConversationState proposedNextState,
        AssistantIntent   classifiedIntent,
        boolean           wasLowNlpConf,
        String            llmAnswerConfidence,
        String            userQuestion,
        String            botAnswer
    ) {
        // ── Repeat detection ─────────────────────────────────────────────────
        boolean isSameIntent = (classifiedIntent != null)
            && (classifiedIntent != AssistantIntent.UNKNOWN)
            && (classifiedIntent == pendingIntent);

        int newRepeatCount = isSameIntent ? repeatCount + 1 : 0;

        // ── State commitment gate ────────────────────────────────────────────
        // Don't advance confirmedState if:
        //   (a) the LLM itself said it was LOW confidence, OR
        //   (b) the user is repeating the same intent (previous answer failed)
        boolean llmWasLow = "LOW".equalsIgnoreCase(llmAnswerConfidence);
        boolean shouldCommitState = !llmWasLow && !isSameIntent;

        ConversationState newConfirmedState = shouldCommitState
            ? proposedNextState
            : confirmedState;   // stay at last known-good state

        // The working state follows the planner's proposal regardless (it affects
        // the current-turn context for the LLM), but confirmedState is the anchor.
        ConversationState newState = proposedNextState;

        // ── Frustration counters ─────────────────────────────────────────────
        int newTurnCount = turnCount + 1;

        int newTroubleshootingTurns = (newState == ConversationState.TROUBLESHOOTING)
            ? troubleshootingTurns + 1
            : 0;

        // Low-conf counter: fire on NLP uncertainty OR LLM low confidence OR repeat
        boolean isFrustrationSignal = wasLowNlpConf || llmWasLow || isSameIntent;
        int newLowConfTurns = isFrustrationSignal ? lowConfidenceTurns + 1 : 0;

        // ── History window ───────────────────────────────────────────────────
        List<HistoryEntry> newHistory = new ArrayList<>(history);
        newHistory.add(new HistoryEntry(userQuestion, botAnswer));
        if (newHistory.size() > MAX_HISTORY) {
            newHistory = newHistory.subList(newHistory.size() - MAX_HISTORY, newHistory.size());
        }

        return new ConversationContext(
            newState,
            newConfirmedState,
            classifiedIntent,   // becomes pendingIntent for next turn
            newRepeatCount,
            newTurnCount,
            newTroubleshootingTurns,
            newLowConfTurns,
            llmAnswerConfidence,
            List.copyOf(newHistory)
        );
    }

    // ── Derived signals ──────────────────────────────────────────────────────

    /** True when the user appears stuck and a human agent should be offered. */
    public boolean shouldEscalate() {
        return troubleshootingTurns >= ESCALATION_THRESHOLD
            || lowConfidenceTurns   >= ESCALATION_THRESHOLD;
    }

    /** True when the user has repeated the same question enough times. */
    public boolean isRepeating() {
        return repeatCount >= REPEAT_THRESHOLD;
    }

    /** True when the previous LLM answer was self-rated LOW. */
    public boolean lastAnswerWasLow() {
        return "LOW".equalsIgnoreCase(lastAnswerConfidence);
    }

    // ── Nested types ─────────────────────────────────────────────────────────

    /** One (question, answer) pair in the sliding history window. */
    public record HistoryEntry(String question, String answer) {}
}
