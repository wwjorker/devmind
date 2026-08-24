package com.devmind.module.ai.service;

public record AskSnapshot(
        Long askLogId,
        String question,
        String answer,
        String retrievalKeyword,
        String modelProvider,
        Boolean mock,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long elapsedMs,
        String reason,
        String expectedAnswer
) {
}
