package com.devmind.module.ai.agent;

public enum AgentRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    BUDGET_EXHAUSTED;

    public boolean isTerminal() {
        return this != RUNNING;
    }
}
