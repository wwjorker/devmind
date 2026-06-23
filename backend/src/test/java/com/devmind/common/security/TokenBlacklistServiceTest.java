package com.devmind.common.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenBlacklistServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void blacklistShouldStoreHashedTokenWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TokenBlacklistService tokenBlacklistService = new TokenBlacklistService(redisTemplate);

        tokenBlacklistService.blacklist("raw.jwt.token", 600L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq("1"), eq(Duration.ofSeconds(600L)));

        String redisKey = keyCaptor.getValue();
        assertThat(redisKey).startsWith("devmind:jwt:blacklist:");
        assertThat(redisKey).doesNotContain("raw.jwt.token");
        assertThat(redisKey.substring("devmind:jwt:blacklist:".length())).hasSize(64);
    }

    @Test
    void isBlacklistedShouldReturnTrueWhenRedisKeyExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        TokenBlacklistService tokenBlacklistService = new TokenBlacklistService(redisTemplate);

        assertThat(tokenBlacklistService.isBlacklisted("raw.jwt.token")).isTrue();
    }

    @Test
    void redisFailureShouldNotBreakLocalDevelopment() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis is down"));
        TokenBlacklistService tokenBlacklistService = new TokenBlacklistService(redisTemplate);

        assertThat(tokenBlacklistService.isBlacklisted("raw.jwt.token")).isFalse();
        assertThatCode(() -> tokenBlacklistService.blacklist("raw.jwt.token", 600L))
                .doesNotThrowAnyException();
    }
}
