package com.devmind.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalEvaluationGuardTest {

    @Test
    void shouldAcquirePerUserCooldownKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(any(), eq("1"), eq(Duration.ofSeconds(120)))).thenReturn(true);
        RetrievalEvaluationGuardProperties properties = properties(120, true);

        RetrievalEvaluationGuard guard = new RetrievalEvaluationGuard(redisTemplate, properties);

        assertThatCode(() -> guard.checkAllowed(7L)).doesNotThrowAnyException();
        verify(operations).setIfAbsent(
                "devmind:guard:retrieval-evaluation:7",
                "1",
                Duration.ofSeconds(120)
        );
    }

    @Test
    void shouldRejectRepeatedEvaluationDuringCooldown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        RetrievalEvaluationGuard guard = new RetrievalEvaluationGuard(
                redisTemplate,
                properties(300, true)
        );

        assertThatThrownBy(() -> guard.checkAllowed(7L))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("300");
    }

    @Test
    void shouldFailOpenWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        RetrievalEvaluationGuard guard = new RetrievalEvaluationGuard(
                redisTemplate,
                properties(300, true)
        );

        assertThatCode(() -> guard.checkAllowed(7L)).doesNotThrowAnyException();
    }

    private RetrievalEvaluationGuardProperties properties(int cooldownSeconds, boolean failOpen) {
        RetrievalEvaluationGuardProperties properties = new RetrievalEvaluationGuardProperties();
        properties.setCooldownSeconds(cooldownSeconds);
        properties.setFailOpen(failOpen);
        return properties;
    }
}
