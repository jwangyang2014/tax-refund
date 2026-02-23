package com.intuit.taxrefund.assistant.nlp;

import com.intuit.taxrefund.assistant.model.AssistantIntent;

public interface IntentClassifier {
    IntentResult classify(String text);

    record IntentResult(AssistantIntent intent, double confidence, String model) {}
}