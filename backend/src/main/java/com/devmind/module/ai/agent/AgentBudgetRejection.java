package com.devmind.module.ai.agent;

public enum AgentBudgetRejection {
    NONE,
    RUN_NOT_ACTIVE,
    DEADLINE_EXCEEDED,
    MAX_STEPS,
    MAX_MODEL_CALLS,
    MAX_TOTAL_TOKENS
}
