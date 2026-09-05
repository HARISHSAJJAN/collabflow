package com.collabflow.auth.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String tokenType) {

    public static AuthResponse bearer(UUID userId, String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthResponse(userId, accessToken, refreshToken, expiresInSeconds, "Bearer");
    }
}
