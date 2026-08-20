package com.devmind.module.ai.agent;

public record AgentTokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public AgentTokenUsage {
        requireNonNegative(promptTokens, "promptTokens");
        requireNonNegative(completionTokens, "completionTokens");
        requireNonNegative(totalTokens, "totalTokens");
        if (totalTokens == null && (promptTokens == null || completionTokens == null)) {
            throw new IllegalArgumentException(
                    "token usage requires totalTokens or both prompt and completion tokens");
        }
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
