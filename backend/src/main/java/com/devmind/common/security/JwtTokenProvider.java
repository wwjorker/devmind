package com.devmind.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "userId";
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getTokenExpireSeconds());
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        return new AuthenticatedUser(userId, claims.getSubject());
    }

    public long getExpireSeconds() {
        return jwtProperties.getTokenExpireSeconds();
    }
}
