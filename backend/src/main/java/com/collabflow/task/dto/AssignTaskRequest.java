package com.collabflow.task.dto;

import java.util.UUID;

/** {@code assigneeId} may be {@code null} to unassign the task. */
public record AssignTaskRequest(UUID assigneeId) {
}
