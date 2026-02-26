package com.intuit.taxrefund.llm;

import java.util.Locale;

public enum LlmProvider {
    MOCK,
    OPENAI,
    GEMINI; // future

    public static LlmProvider from(String s) {
        if (s == null || s.isBlank()) return MOCK;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "openai" -> OPENAI;
            case "gemini" -> GEMINI;
            default -> MOCK;
        };
    }
}