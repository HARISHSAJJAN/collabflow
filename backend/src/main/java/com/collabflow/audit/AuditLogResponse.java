package com.collabflow.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        String oldValue,
        String newValue,
        Instant createdAt) {
}
