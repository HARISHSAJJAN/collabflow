package com.collabflow.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, String userAgent, String ipAddress, Instant issuedAt, Instant expiresAt) {
}
