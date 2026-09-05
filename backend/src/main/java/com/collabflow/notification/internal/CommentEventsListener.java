package com.collabflow.notification.internal;

import com.collabflow.comment.CommentCreatedEvent;
import com.collabflow.notification.NotificationService;
import com.collabflow.notification.NotificationType;
import com.collabflow.task.TaskService;
import com.collabflow.task.TaskSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link com.collabflow.kafka.KafkaTopics#COMMENT_EVENTS} - a single payload type, so
 * no class-level {@code @KafkaListener} + {@code @KafkaHandler} multiplexing is needed here
 * (contrast with {@link TaskEventsListener}); a plain method-level listener is enough.
 *
 * <p>Notifies the task's assignee and reporter - excluding the comment's own author, and
 * de-duplicated if they're the same person (e.g. the reporter commenting on their own
 * unassigned task notifies no one, correctly).</p>
 */
@Component
public class CommentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CommentEventsListener.class);

    private final NotificationService notificationService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public CommentEventsListener(NotificationService notificationService, TaskService taskService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "collabflow.comment-events", groupId = "notification-service")
    public void onCommentCreated(CommentCreatedEvent event, @Header("eventId") String eventId) {
        TaskSummary task = taskService.requireTaskSummary(event.taskId());

        Set<UUID> recipients = new LinkedHashSet<>();
        if (task.assigneeId() != null) {
            recipients.add(task.assigneeId());
        }
        recipients.add(task.reporterId());
        recipients.remove(event.authorId());

        Map<String, Object> payload = Map.of(
                "commentId", event.commentId(), "taskId", event.taskId(), "projectId", event.projectId(), "authorId", event.authorId());

        for (UUID recipientId : recipients) {
            try {
                // Each recipient shares the same eventId - would collide on the
                // processed_events primary key after the first insert. A per-recipient
                // idempotency key (derived, not random) keeps each recipient's notification
                // independently idempotent under redelivery without losing the fan-out.
                UUID recipientEventId = UUID.nameUUIDFromBytes((eventId + ":" + recipientId).getBytes());
                notificationService.recordEventAndNotify(
                        recipientEventId, recipientId, NotificationType.COMMENT_ADDED, objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                log.error("Failed to write comment notification for event {} recipient {}", eventId, recipientId, e);
                throw new RuntimeException(e);
            }
        }
    }
}
