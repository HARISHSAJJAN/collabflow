package com.collabflow.task;

import java.util.UUID;

/** What the comment module (Phase 8) needs to know about a task - existence and its project - without touching task.internal. */
public record TaskSummary(UUID id, UUID projectId, UUID assigneeId, UUID reporterId) {
}
