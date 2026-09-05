package com.collabflow.notification;

/**
 * What's implemented now vs. deliberately deferred - see docs/kafka.md and docs/decisions.md:
 * {@code TASK_MENTION} (parsing @mentions out of comment text) and
 * {@code DUE_DATE_APPROACHING} (needs a scheduled job, not an event - nothing "happens" to
 * trigger it) are not yet produced by anything, even though the type exists here as a
 * placeholder for when they are.
 */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    COMMENT_ADDED,
    TEAM_MEMBER_ADDED,
    TASK_MENTION,
    DUE_DATE_APPROACHING
}
