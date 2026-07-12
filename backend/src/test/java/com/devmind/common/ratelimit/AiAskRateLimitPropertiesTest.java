package com.devmind.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AiAskRateLimitPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitPropertiesConfiguration.class);

    @Test
    void shouldRejectNonPositiveLimitDuringApplicationStartup() {
        contextRunner
                .withPropertyValues("devmind.rate-limit.ai-ask.requests-per-minute=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiAskRateLimitProperties.class)
    static class RateLimitPropertiesConfiguration {
    }
}
