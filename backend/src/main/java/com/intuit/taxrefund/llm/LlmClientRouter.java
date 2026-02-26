package com.intuit.taxrefund.llm;

import org.springframework.stereotype.Component;

@Component
public class LlmClientRouter {

    private final AiProps props;
    private final MockLlmClient mock;
    private final OpenAiLlmClient openai;

    public LlmClientRouter(AiProps props, MockLlmClient mock, OpenAiLlmClient openai) {
        this.props = props;
        this.mock = mock;
        this.openai = openai;
    }

    public LlmClient primary() {
        LlmProvider p = LlmProvider.from(props.llmProvider());
        return switch (p) {
            case OPENAI -> openai;
            case GEMINI -> mock; // until implemented
            default -> mock;
        };
    }

    public String callWithFallback(String developerPrompt, String userPrompt, java.util.Map<String, Object> schema) {
        LlmClient primary = primary();
        if (primary.isAvailable()) {
            try {
                return primary.generateStructuredJson(developerPrompt, userPrompt, schema);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return mock.generateStructuredJson(developerPrompt, userPrompt, schema);
    }
}