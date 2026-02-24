package com.intuit.taxrefund.assistant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
    @NotBlank
    @Size(max = 500)
    String question
) {}