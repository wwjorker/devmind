package com.devmind.common.security;

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

    private static final String KEY_PREFIX = "devmind:jwt:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    public TokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void blacklist(String token, long ttlSeconds) {
        if (!StringUtils.hasText(token) || ttlSeconds <= 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(toKey(token), "1", Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ignored) {
            // Keep local development usable when Redis is not running.
        }
    }

    public boolean isBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(toKey(token)));
        } catch (RuntimeException ignored) {
            return false;
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
