package com.collabflow.kafka;

/**
 * One topic per aggregate domain, not one topic per event type. {@code TaskCreatedEvent},
 * {@code TaskAssignedEvent}, {@code TaskStatusChangedEvent}, and
 * {@code TaskPriorityChangedEvent} all share {@link #TASK_EVENTS} - a common, realistic Kafka
 * pattern that avoids topic sprawl while still letting a consumer subscribe to "everything
 * about tasks" with one subscription. Messages are keyed by the aggregate id (the task id,
 * project id, etc.) so that every event about the same aggregate lands on the same partition
 * and is therefore delivered to any one consumer in the order it was produced - see
 * docs/kafka.md's "ordering" section for exactly what guarantee this does and does not give.
 */
public final class KafkaTopics {

    public static final String TASK_EVENTS = "collabflow.task-events";
    public static final String PROJECT_EVENTS = "collabflow.project-events";
    public static final String TEAM_EVENTS = "collabflow.team-events";
    public static final String COMMENT_EVENTS = "collabflow.comment-events";

    private KafkaTopics() {
    }
}
