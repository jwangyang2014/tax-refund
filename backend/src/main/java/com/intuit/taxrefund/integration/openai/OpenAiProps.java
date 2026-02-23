package com.intuit.taxrefund.integration.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiProps(
    String apiKey,
    String model
) {}