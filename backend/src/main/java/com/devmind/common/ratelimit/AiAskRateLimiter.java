package com.devmind.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AiAskRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiAskRateLimiter.class);
    private static final long WINDOW_SECONDS = 60L;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiAskRateLimitProperties properties;
    private final Clock clock;

    @Autowired
    public AiAskRateLimiter(StringRedisTemplate redisTemplate,
                            AiAskRateLimitProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    AiAskRateLimiter(StringRedisTemplate redisTemplate,
                     AiAskRateLimitProperties properties,
                     Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkAllowed(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getRequestsPerMinute() <= 0) {
            throw new IllegalStateException("AI ask rate limit must be greater than zero");
        }

        String key = keyFor(userId, clock.instant());
        try {
            Long requestCount = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    List.of(key),
                    Long.toString(WINDOW_SECONDS)
            );
            if (requestCount == null) {
                throw new IllegalStateException("Redis returned no rate-limit counter");
            }
            if (requestCount > properties.getRequestsPerMinute()) {
                throw new RateLimitExceededException(
                        "AI ask rate limit exceeded: max " + properties.getRequestsPerMinute() + " requests per minute"
                );
            }
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Failed to enforce AI ask rate limit. userId={}, failOpen={}",
                    userId, properties.isFailOpen(), ex);
            if (!properties.isFailOpen()) {
                throw new RateLimitUnavailableException("AI ask rate limiter is unavailable", ex);
            }
        }
    }

    private String keyFor(Long userId, Instant now) {
        long epochMinute = now.getEpochSecond() / WINDOW_SECONDS;
        return "devmind:rate-limit:ai-ask:" + userId + ":" + epochMinute;
    }
}
