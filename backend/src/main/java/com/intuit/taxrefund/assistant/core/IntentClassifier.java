package com.intuit.taxrefund.assistant.core;

public interface IntentClassifier {
    IntentResult classify(String text);

    record IntentResult(AssistantIntent intent, double confidence, String model) {}
}