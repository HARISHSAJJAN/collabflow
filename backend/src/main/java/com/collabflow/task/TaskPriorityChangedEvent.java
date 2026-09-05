package com.collabflow.task;

import java.util.UUID;

public record TaskPriorityChangedEvent(UUID taskId, UUID projectId, TaskPriority oldPriority, TaskPriority newPriority, UUID changedBy) {
}
