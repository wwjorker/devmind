package com.devmind.module.ai.agent;

public record AgentTokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
