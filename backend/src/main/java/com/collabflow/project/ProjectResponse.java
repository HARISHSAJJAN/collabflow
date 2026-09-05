package com.collabflow.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID teamId,
        String name,
        String description,
        ProjectStatus status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
