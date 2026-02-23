package com.intuit.taxrefund.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProps(
    String provider,          // mock | openai | gemini (future)
    OpenAi openai
) {
    public record OpenAi(
        String apiKey,
        String model
    ) {}
}