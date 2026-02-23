package com.intuit.taxrefund.llm;

import java.util.Map;

public interface LlmClient {

    /**
     * A stable provider id: "mock", "openai", "gemini", etc.
     */
    String provider();

    /**
     * Model identifier, e.g. "gpt-4o-mini" or "mock-v1".
     */
    String model();

    /**
     * Whether this client is usable given current config (e.g. API key exists).
     */
    boolean isAvailable();

    /**
     * Generate JSON text that must conform to the provided JSON schema.
     * Implementations should throw on failures (router handles fallback).
     */
    String generateStructuredJson(String developerPrompt, String userPrompt, Map<String, Object> jsonSchema);
}