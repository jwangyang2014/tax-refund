package com.intuit.taxrefund.assistant.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantChatResponse(
    @JsonProperty("answerMarkdown") String answerMarkdown,
    @JsonProperty("citations") List<Citation> citations,
    @JsonProperty("actions") List<Action> actions,
    @JsonProperty("confidence") Confidence confidence
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Citation(
        @JsonProperty("docId") String docId,
        @JsonProperty("quote") String quote
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(
        @JsonProperty("type") ActionType type,
        @JsonProperty("label") String label
    ) {}

    public enum ActionType { REFRESH, CONTACT_SUPPORT, SHOW_TRACKING }
    public enum Confidence { LOW, MEDIUM, HIGH }
}