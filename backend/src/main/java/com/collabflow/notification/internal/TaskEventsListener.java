package com.collabflow.notification.internal;

import com.collabflow.notification.NotificationService;
import com.collabflow.notification.NotificationType;
import com.collabflow.task.TaskAssignedEvent;
import com.collabflow.task.TaskCreatedEvent;
import com.collabflow.task.TaskPriorityChangedEvent;
import com.collabflow.task.TaskService;
import com.collabflow.task.TaskStatusChangedEvent;
import com.collabflow.task.TaskSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link com.collabflow.kafka.KafkaTopics#TASK_EVENTS}. One {@code @KafkaListener}
 * class per topic, with one {@code @KafkaHandler} method per payload type that topic carries -
 * Spring Kafka dispatches to the right method based on the deserialized payload's type (see
 * application.yml's {@code spring.json.add.type.headers}). {@code TaskCreatedEvent} and
 * {@code TaskPriorityChangedEvent} are handled but deliberately produce no notification -
 * they're still routed here (every type on this topic must have a handler, or Spring Kafka
 * has nothing to dispatch it to) but aren't judged notification-worthy on their own.
 *
 * <p>Consumer group {@code notification-service}, distinct from other consumer groups this
 * codebase might add later for the same topic (e.g. an analytics consumer) - each consumer
 * group gets its own copy of every message and tracks its own offsets independently. See
 * docs/kafka.md.</p>
 */
@Component
@KafkaListener(topics = "collabflow.task-events", groupId = "notification-service")
public class TaskEventsListener {

    private static final Logger log = LoggerFactory.getLogger(TaskEventsListener.class);

    private final NotificationService notificationService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskEventsListener(NotificationService notificationService, TaskService taskService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @KafkaHandler
    public void onTaskCreated(TaskCreatedEvent event) {
        log.debug("Task created: {} (no notification produced for this event type)", event.taskId());
    }

    @KafkaHandler
    public void onTaskAssigned(TaskAssignedEvent event, @Header("eventId") String eventId) {
        if (event.assigneeId() == null || event.assigneeId().equals(event.assignedBy())) {
            return; // unassigned, or self-assigned - nothing to notify anyone about
        }
        notify(eventId, event.assigneeId(), NotificationType.TASK_ASSIGNED,
                Map.of("taskId", event.taskId(), "projectId", event.projectId(), "assignedBy", event.assignedBy()));
    }

    @KafkaHandler
    public void onTaskStatusChanged(TaskStatusChangedEvent event, @Header("eventId") String eventId) {
        // The event payload doesn't carry the current assignee, so this listener asks the
        // task module directly (TaskService.requireTaskSummary) - a normal cross-module public
        // API call, the same as any other module makes; a Kafka consumer calling into another
        // module's service isn't materially different from a REST controller doing so.
        TaskSummary task = taskService.requireTaskSummary(event.taskId());
        if (task.assigneeId() == null || task.assigneeId().equals(event.changedBy())) {
            return; // unassigned, or the assignee is the one who made the change
        }
        notify(eventId, task.assigneeId(), NotificationType.TASK_STATUS_CHANGED,
                Map.of("taskId", event.taskId(), "projectId", event.projectId(),
                        "oldStatus", event.oldStatus(), "newStatus", event.newStatus(), "changedBy", event.changedBy()));
    }

    @KafkaHandler
    public void onTaskPriorityChanged(TaskPriorityChangedEvent event) {
        log.debug("Task priority changed: {} (no notification produced for this event type)", event.taskId());
    }

    private void notify(String eventId, UUID recipientId, NotificationType type, Map<String, Object> payload) {
        try {
            notificationService.recordEventAndNotify(UUID.fromString(eventId), recipientId, type, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to write notification for event {}", eventId, e);
            throw new RuntimeException(e); // let Spring Kafka's error handler retry / dead-letter this
        }
    }
}
