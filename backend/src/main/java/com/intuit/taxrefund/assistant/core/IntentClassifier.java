package com.intuit.taxrefund.assistant.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {
    private static final Logger log = LogManager.getLogger(IntentClassifier.class);

    public AssistantIntent classify(String text) {
        String t = text == null ? "" : text.toLowerCase();

        AssistantIntent out;
        if (t.contains("status") || t.contains("where is my refund") || t.contains("latest")) out = AssistantIntent.REFUND_STATUS;
        else if (t.contains("eta") || t.contains("when") || t.contains("how long") || t.contains("available")) out = AssistantIntent.REFUND_ETA;
        else if (t.contains("why") || t.contains("delayed") || t.contains("stuck") || t.contains("processing")) out = AssistantIntent.WHY_DELAYED;
        else if (t.contains("next step") || t.contains("what should i do") || t.contains("action")) out = AssistantIntent.NEXT_STEPS;
        else out = AssistantIntent.UNKNOWN;

        log.debug("intent_classified intent={}", out);
        return out;
    }
}