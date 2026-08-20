package com.devmind.module.ai.agent;

import java.time.Duration;
import java.util.Objects;

public record AgentBudgetLimits(
        int maxSteps,
        int maxModelCalls,
        int maxTotalTokens,
        Duration timeout
) {

    public static final int ABSOLUTE_MAX_STEPS = 20;
    public static final int ABSOLUTE_MAX_MODEL_CALLS = 20;
    public static final int ABSOLUTE_MAX_TOTAL_TOKENS = 1_000_000;
    public static final Duration ABSOLUTE_MAX_TIMEOUT = Duration.ofMinutes(10);

    public AgentBudgetLimits {
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        requireRange(maxSteps, 1, ABSOLUTE_MAX_STEPS, "maxSteps");
        requireRange(maxModelCalls, 1, ABSOLUTE_MAX_MODEL_CALLS, "maxModelCalls");
        requireRange(maxTotalTokens, 1, ABSOLUTE_MAX_TOTAL_TOKENS, "maxTotalTokens");
        if (timeout.toMillis() < 1 || timeout.compareTo(ABSOLUTE_MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout must be between 1 ms and "
                    + ABSOLUTE_MAX_TIMEOUT.toMillis() + " ms");
        }
    }

    public static AgentBudgetLimits triageDefaults() {
        // Preregistered fairness budget: at most 6 model calls plus 12 tool calls.
        return new AgentBudgetLimits(18, 6, 24_000, Duration.ofSeconds(120));
    }

    private static void requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }
}
