package com.devmind.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void createTokenAndParseTokenShouldKeepUserIdentity() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setJwtSecret("devmind-test-secret-key-must-be-at-least-32-chars");
        jwtProperties.setTokenExpireSeconds(3600);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);

        String token = jwtTokenProvider.createToken(12L, "testuser");
        AuthenticatedUser authenticatedUser = jwtTokenProvider.parseToken(token);

        assertThat(token).isNotBlank();
        assertThat(authenticatedUser.userId()).isEqualTo(12L);
        assertThat(authenticatedUser.username()).isEqualTo("testuser");
        assertThat(jwtTokenProvider.getExpireSeconds()).isEqualTo(3600);
    }
}
