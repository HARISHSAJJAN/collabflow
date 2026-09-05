package com.collabflow.notification.internal;

import com.collabflow.notification.NotificationService;
import com.collabflow.notification.NotificationType;
import com.collabflow.team.MemberAddedEvent;
import com.collabflow.team.MemberRemovedEvent;
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
 * Consumes {@link com.collabflow.kafka.KafkaTopics#TEAM_EVENTS}. Only {@code MemberAddedEvent}
 * produces a notification (the closest match to the brief's "project invitation" notification
 * type, given team membership is added immediately rather than via a pending-invite flow -
 * see docs/decisions.md). {@code MemberRemovedEvent} is handled (every type on this topic
 * needs a handler) but deliberately produces none - notifying someone "you were removed" was
 * judged not worth the awkward UX for this project's scope, though it would be a reasonable
 * addition.
 */
@Component
@KafkaListener(topics = "collabflow.team-events", groupId = "notification-service")
public class TeamEventsListener {

    private static final Logger log = LoggerFactory.getLogger(TeamEventsListener.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public TeamEventsListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaHandler
    public void onMemberAdded(MemberAddedEvent event, @Header("eventId") String eventId) {
        try {
            Map<String, Object> payload = Map.of("teamId", event.teamId(), "addedBy", event.addedBy());
            notificationService.recordEventAndNotify(
                    UUID.fromString(eventId), event.userId(), NotificationType.TEAM_MEMBER_ADDED, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to write notification for event {}", eventId, e);
            throw new RuntimeException(e);
        }
    }

    @KafkaHandler
    public void onMemberRemoved(MemberRemovedEvent event) {
        log.debug("Member {} removed from team {} (no notification produced for this event type)", event.userId(), event.teamId());
    }
}
