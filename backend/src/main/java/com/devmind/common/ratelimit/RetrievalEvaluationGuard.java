package com.devmind.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RetrievalEvaluationGuard {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluationGuard.class);

    private final StringRedisTemplate redisTemplate;
    private final RetrievalEvaluationGuardProperties properties;

    public RetrievalEvaluationGuard(StringRedisTemplate redisTemplate,
                                    RetrievalEvaluationGuardProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void checkAllowed(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }

        String key = "devmind:guard:retrieval-evaluation:" + userId;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    "1",
                    Duration.ofSeconds(properties.getCooldownSeconds())
            );
            if (!Boolean.TRUE.equals(acquired)) {
                throw new RateLimitExceededException(
                        "检索评估正在冷却中，请在 "
                                + properties.getCooldownSeconds()
                                + " 秒后再试"
                );
            }
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Failed to enforce retrieval evaluation guard. userId={}, failOpen={}",
                    userId, properties.isFailOpen(), ex);
            if (!properties.isFailOpen()) {
                throw new RateLimitUnavailableException("Retrieval evaluation guard is unavailable", ex);
            }
        }
    }
}
