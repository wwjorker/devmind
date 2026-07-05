package com.devmind.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String KEY_PREFIX = "devmind:jwt:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean failOpen;

    public TokenBlacklistService(StringRedisTemplate stringRedisTemplate,
                                 @Value("${devmind.security.blacklist-fail-open:true}") boolean failOpen) {
        this.stringRedisTemplate = stringRedisTemplate;
        // Fail-open keeps local development usable when Redis is not running: a Redis
        // failure only disables logout revocation. Deployments that must guarantee a
        // logged-out token can never be reused should set the property to false, which
        // rejects requests whenever the blacklist cannot be checked (availability cost).
        this.failOpen = failOpen;
    }

    public void blacklist(String token, long ttlSeconds) {
        if (!StringUtils.hasText(token) || ttlSeconds <= 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(toKey(token), "1", Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ex) {
            log.warn("Failed to write JWT logout blacklist entry to Redis. ttlSeconds={}", ttlSeconds, ex);
            if (!failOpen) {
                throw ex;
            }
        }
    }

    public boolean isBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(toKey(token)));
        } catch (RuntimeException ex) {
            log.warn("Failed to check JWT logout blacklist from Redis. failOpen={}", failOpen, ex);
            return !failOpen;
        }
    }

    private String toKey(String token) {
        String tokenHash = sha256(token);
        return KEY_PREFIX + tokenHash;
    }

    private String sha256(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
