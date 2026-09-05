package com.collabflow.task;

import java.util.UUID;

/** {@code assigneeId} is {@code null} when the task was unassigned. Consumed by audit (Phase 8) and notification (Phase 11). */
public record TaskAssignedEvent(UUID taskId, UUID projectId, UUID assigneeId, UUID assignedBy) {
}
