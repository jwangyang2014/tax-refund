package com.intuit.taxrefund.assistant.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProps(
    int maxQuestionChars,
    int dailyOpenAiCallsPerUser
) {}