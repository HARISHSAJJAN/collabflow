package com.collabflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A pure unit test - no Spring context, no database, no Testcontainers - for exactly the kind
 * of self-contained business logic the brief's "unit tests" category describes. JwtService
 * has no collaborators to mock; its whole job is deterministic given a secret and a clock, so
 * a plain {@code new JwtService(...)} is enough.
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService("unit-test-secret-at-least-32-bytes-long-for-hs256", 15);

    @Test
    void generatesATokenThatValidatesBackToTheSameUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "someone@example.com");

        assertThat(jwtService.validateAndExtractUserId(token)).contains(userId);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        UUID userId = UUID.randomUUID();
        JwtService otherIssuer = new JwtService("a-completely-different-secret-of-sufficient-length-too", 15);
        String token = otherIssuer.generateAccessToken(userId, "someone@example.com");

        assertThat(jwtService.validateAndExtractUserId(token)).isEmpty();
    }

    @Test
    void rejectsATamperedToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "someone@example.com");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtService.validateAndExtractUserId(tampered)).isEmpty();
    }

    @Test
    void rejectsGarbageInput() {
        assertThat(jwtService.validateAndExtractUserId("not-a-jwt-at-all")).isEmpty();
        assertThat(jwtService.validateAndExtractUserId("")).isEmpty();
    }

    @Test
    void exposesTheConfiguredTtlInSeconds() {
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(15 * 60L);
    }
}
