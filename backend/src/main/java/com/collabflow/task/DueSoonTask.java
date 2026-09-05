package com.collabflow.task;

import java.time.LocalDate;
import java.util.UUID;

/** What {@code notification.internal.DueDateReminderJob} (Phase 11) needs to build a due-date-approaching notification. */
public record DueSoonTask(UUID taskId, UUID projectId, UUID assigneeId, String title, LocalDate dueDate) {
}
