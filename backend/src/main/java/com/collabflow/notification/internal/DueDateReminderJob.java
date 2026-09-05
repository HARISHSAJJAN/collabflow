package com.collabflow.notification.internal;

import com.collabflow.notification.NotificationService;
import com.collabflow.notification.NotificationType;
import com.collabflow.task.DueSoonTask;
import com.collabflow.task.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The one notification type in this codebase not triggered by any event - see
 * {@code NotificationType}'s Javadoc: nothing "happens" when a due date gets close, so it has
 * to be found by a periodic sweep instead of reacted to. Runs once daily; "approaching" is
 * defined as "due tomorrow," which is checked exactly once per calendar day, so the
 * idempotency key here (derived from task id + date, the same
 * {@code UUID.nameUUIDFromBytes} pattern used for comment fan-out in
 * {@code CommentEventsListener}) is really a defense against the job somehow running twice on
 * the same day (e.g. an app restart shortly after a scheduled run), not against genuine Kafka
 * redelivery - there's no Kafka message here at all.
 */
@Component
public class DueDateReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DueDateReminderJob.class);

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public DueDateReminderJob(TaskService taskService, NotificationService notificationService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /** Every day at 08:00 server time. */
    @Scheduled(cron = "0 0 8 * * *")
    public void remindForTasksDueTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        var dueSoon = taskService.findTasksDueOn(tomorrow);
        log.info("Due-date sweep for {}: {} task(s) due", tomorrow, dueSoon.size());

        for (DueSoonTask task : dueSoon) {
            try {
                UUID idempotencyKey = UUID.nameUUIDFromBytes(("due-date:" + task.taskId() + ":" + tomorrow).getBytes(StandardCharsets.UTF_8));
                Map<String, Object> payload = Map.of(
                        "taskId", task.taskId(), "projectId", task.projectId(), "title", task.title(), "dueDate", task.dueDate());
                notificationService.recordEventAndNotify(
                        idempotencyKey, task.assigneeId(), NotificationType.DUE_DATE_APPROACHING, objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                log.error("Failed to write due-date reminder for task {}", task.taskId(), e);
            }
        }
    }
}
