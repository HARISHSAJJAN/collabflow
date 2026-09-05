package com.collabflow.notification.internal;

import com.collabflow.comment.CommentCreatedEvent;
import com.collabflow.notification.NotificationService;
import com.collabflow.notification.NotificationType;
import com.collabflow.project.ProjectService;
import com.collabflow.task.TaskService;
import com.collabflow.task.TaskSummary;
import com.collabflow.user.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>Two independent notification reasons come out of one comment:</p>
 * <ul>
 *   <li><b>Fan-out</b>: the task's assignee and reporter (excluding the comment's own author,
 *       de-duplicated if they're the same person).</li>
 *   <li><b>@mentions</b> (Phase 11): {@code @user@example.com}-style tokens in the comment
 *       body. This project has no username/handle system, so mentions are matched on exact
 *       email address - a deliberate scope limitation (no autocomplete, no partial-name
 *       matching) rather than a hidden gap; see docs/decisions.md. A mentioned user only gets
 *       notified if they're an actual project member - mentioning an email that isn't a
 *       project member (a typo, an outsider) silently notifies no one, rather than leaking
 *       whether that email even has an account.</li>
 * </ul>
 */
@Component
public class CommentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CommentEventsListener.class);

    // A conservative, deliberately simple email-shaped token preceded by '@' - good enough for
    // "mention someone by their exact email," not a full RFC 5322 validator.
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,})");

    private final NotificationService notificationService;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final UserAccountService userAccountService;
    private final ObjectMapper objectMapper;

    public CommentEventsListener(
            NotificationService notificationService,
            TaskService taskService,
            ProjectService projectService,
            UserAccountService userAccountService,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.taskService = taskService;
        this.projectService = projectService;
        this.userAccountService = userAccountService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "collabflow.comment-events", groupId = "notification-service")
    public void onCommentCreated(CommentCreatedEvent event, @Header("eventId") String eventId) {
        TaskSummary task = taskService.requireTaskSummary(event.taskId());

        Set<UUID> fanOutRecipients = new LinkedHashSet<>();
        if (task.assigneeId() != null) {
            fanOutRecipients.add(task.assigneeId());
        }
        fanOutRecipients.add(task.reporterId());
        fanOutRecipients.remove(event.authorId());

        Map<String, Object> commentPayload = Map.of(
                "commentId", event.commentId(), "taskId", event.taskId(), "projectId", event.projectId(), "authorId", event.authorId());
        for (UUID recipientId : fanOutRecipients) {
            notifyOnce("comment:" + eventId + ":" + recipientId, recipientId, NotificationType.COMMENT_ADDED, commentPayload);
        }

        Map<String, Object> mentionPayload = Map.of(
                "commentId", event.commentId(), "taskId", event.taskId(), "projectId", event.projectId(), "authorId", event.authorId());
        for (UUID mentionedUserId : resolveMentions(event)) {
            notifyOnce("mention:" + eventId + ":" + mentionedUserId, mentionedUserId, NotificationType.TASK_MENTION, mentionPayload);
        }
    }

    private Set<UUID> resolveMentions(CommentCreatedEvent event) {
        Set<UUID> mentioned = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(event.body());
        while (matcher.find()) {
            String email = matcher.group(1);
            userAccountService.findCredentialsByEmail(email)
                    .map(c -> c.userId())
                    .filter(userId -> !userId.equals(event.authorId()))
                    .filter(userId -> projectService.isProjectMember(event.projectId(), userId))
                    .ifPresent(mentioned::add);
        }
        return mentioned;
    }

    private void notifyOnce(String idempotencySeed, UUID recipientId, NotificationType type, Map<String, Object> payload) {
        try {
            // Each recipient/reason shares the same underlying Kafka eventId - would collide
            // on the processed_events primary key after the first insert. A derived,
            // deterministic per-(reason, recipient) key keeps each independently idempotent
            // under redelivery without losing the fan-out. See docs/kafka.md.
            UUID idempotencyKey = UUID.nameUUIDFromBytes(idempotencySeed.getBytes());
            notificationService.recordEventAndNotify(idempotencyKey, recipientId, type, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to write {} notification for recipient {} (seed {})", type, recipientId, idempotencySeed, e);
            throw new RuntimeException(e);
        }
    }
}
