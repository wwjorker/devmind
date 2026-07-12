package com.devmind.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAskRateLimiterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-12T00:00:30Z"), ZoneOffset.UTC
    );

    @Test
    void shouldAllowRequestsWithinLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenReturn(10L);
        AiAskRateLimiter limiter = limiter(redisTemplate, properties(true, 10, true));

        assertThatCode(() -> limiter.checkAllowed(7L)).doesNotThrowAnyException();

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(java.util.List.of("devmind:rate-limit:ai-ask:7:"
                        + FIXED_CLOCK.instant().getEpochSecond() / 60)),
                eq("60")
        );
    }

    @Test
    void shouldRejectRequestsAboveLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenReturn(11L);
        AiAskRateLimiter limiter = limiter(redisTemplate, properties(true, 10, true));

        assertThatThrownBy(() -> limiter.checkAllowed(7L))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("max 10 requests per minute");
    }

    @Test
    void shouldFailOpenWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenThrow(new RuntimeException("redis is down"));
        AiAskRateLimiter limiter = limiter(redisTemplate, properties(true, 10, true));

        assertThatCode(() -> limiter.checkAllowed(7L)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailClosedWhenConfiguredAndRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenThrow(new RuntimeException("redis is down"));
        AiAskRateLimiter limiter = limiter(redisTemplate, properties(true, 10, false));

        assertThatThrownBy(() -> limiter.checkAllowed(7L))
                .isInstanceOf(RateLimitUnavailableException.class);
    }

    @Test
    void shouldFailClosedWhenRedisReturnsNoCounter() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenReturn(null);
        AiAskRateLimiter limiter = limiter(redisTemplate, properties(true, 10, false));

        assertThatThrownBy(() -> limiter.checkAllowed(7L))
                .isInstanceOf(RateLimitUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void shouldSkipRedisWhenDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AiAskRateLimiter limiter = limiter(redisTemplate, properties(false, 10, true));

        limiter.checkAllowed(7L);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), (Object[]) any());
    }

    private AiAskRateLimiter limiter(StringRedisTemplate redisTemplate,
                                     AiAskRateLimitProperties properties) {
        return new AiAskRateLimiter(redisTemplate, properties, FIXED_CLOCK);
    }

    private AiAskRateLimitProperties properties(boolean enabled, int limit, boolean failOpen) {
        AiAskRateLimitProperties properties = new AiAskRateLimitProperties();
        properties.setEnabled(enabled);
        properties.setRequestsPerMinute(limit);
        properties.setFailOpen(failOpen);
        return properties;
    }
}
