package com.devmind.module.ai.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTokenUsageTest {

    @Test
    void shouldAcceptEitherProviderTotalOrBothComponentCounts() {
        assertThat(new AgentTokenUsage(null, null, 12).totalTokens()).isEqualTo(12);
        assertThat(new AgentTokenUsage(8, 4, null).totalTokens()).isNull();
    }

    @Test
    void shouldRejectUnmeasurableOrNegativeUsage() {
        assertThatThrownBy(() -> new AgentTokenUsage(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires totalTokens");
        assertThatThrownBy(() -> new AgentTokenUsage(1, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completionTokens");
    }
}
