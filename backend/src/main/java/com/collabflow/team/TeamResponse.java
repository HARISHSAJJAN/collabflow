package com.collabflow.team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String description,
        UUID createdBy,
        TeamRole myRole,
        Instant createdAt,
        Instant updatedAt) {
}
