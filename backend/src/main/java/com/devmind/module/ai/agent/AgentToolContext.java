package com.devmind.module.ai.agent;

public record AgentToolContext(Long userId, Long runId) {

    public AgentToolContext {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (runId == null || runId <= 0) {
            throw new IllegalArgumentException("runId must be positive");
        }
    }
}
