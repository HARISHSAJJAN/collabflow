package com.collabflow.notification;

/**
 * All six types are produced as of Phase 11 - see the module's internal listeners:
 * {@code TaskEventsListener} ({@code TASK_ASSIGNED}, {@code TASK_STATUS_CHANGED}),
 * {@code CommentEventsListener} ({@code COMMENT_ADDED}, {@code TASK_MENTION}),
 * {@code TeamEventsListener} ({@code TEAM_MEMBER_ADDED}), and
 * {@code DueDateReminderJob} ({@code DUE_DATE_APPROACHING} - the one type not triggered by any
 * event, since nothing "happens" for it; it's found by a daily scheduled sweep instead).
 */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    COMMENT_ADDED,
    TEAM_MEMBER_ADDED,
    TASK_MENTION,
    DUE_DATE_APPROACHING
}
