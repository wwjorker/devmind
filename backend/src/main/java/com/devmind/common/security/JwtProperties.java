package com.devmind.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devmind.security")
public class JwtProperties {

    private String jwtSecret;
    private long tokenExpireSeconds;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getTokenExpireSeconds() {
        return tokenExpireSeconds;
    }

    public void setTokenExpireSeconds(long tokenExpireSeconds) {
        this.tokenExpireSeconds = tokenExpireSeconds;
    }
}
