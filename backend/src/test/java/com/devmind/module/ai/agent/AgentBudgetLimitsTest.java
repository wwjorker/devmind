package com.devmind.module.ai.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBudgetLimitsTest {

    @Test
    void triageDefaultsShouldMatchThePreregisteredFairnessBudget() {
        AgentBudgetLimits limits = AgentBudgetLimits.triageDefaults();

        assertThat(limits.maxSteps()).isEqualTo(18);
        assertThat(limits.maxModelCalls()).isEqualTo(6);
        assertThat(limits.maxTotalTokens()).isEqualTo(24_000);
        assertThat(limits.timeout()).isEqualTo(Duration.ofSeconds(120));
    }
}
