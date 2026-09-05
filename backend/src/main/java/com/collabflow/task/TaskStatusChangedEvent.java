package com.collabflow.task;

import java.util.UUID;

public record TaskStatusChangedEvent(UUID taskId, UUID projectId, TaskStatus oldStatus, TaskStatus newStatus, UUID changedBy) {
}
