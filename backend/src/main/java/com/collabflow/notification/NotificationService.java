package com.collabflow.notification;

import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.common.web.PageResponse;
import com.collabflow.notification.internal.Notification;
import com.collabflow.notification.internal.NotificationRepository;
import com.collabflow.notification.internal.ProcessedEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API: read-only queries for "my notifications," and the one write path
 * used by {@code notification.internal.NotificationEventListener} to turn a Kafka event into
 * a notification exactly once even under at-least-once delivery.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(NotificationRepository notificationRepository, ProcessedEventRepository processedEventRepository) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Records that {@code eventId} has been processed and creates the resulting notification,
     * atomically, in a single transaction. If {@code eventId} was already recorded (a
     * redelivered/duplicate Kafka message - normal under at-least-once delivery, not an
     * error), this is a safe no-op and returns {@code false} - no second notification is
     * created.
     *
     * <p>The dedup check ({@code ProcessedEventRepository.tryInsert}, an atomic {@code INSERT
     * ... ON CONFLICT DO NOTHING}) is deliberately exception-free - see two real bugs found by
     * testing this with actual message redelivery (reset a consumer group's offsets and let
     * the app reconsume already-processed messages - see docs/troubleshooting.md), in order:</p>
     * <ol>
     *   <li>The first version used a plain {@code save()} on an entity with a manually-
     *       assigned (non-generated) id, which Spring Data JPA quietly turned into a
     *       {@code merge()} - an upsert, not an insert - so a "duplicate" key never actually
     *       conflicted and the dedup check never triggered at all.</li>
     *   <li>After fixing that (making {@code save()} genuinely attempt an insert), the
     *       resulting constraint-violation exception, even caught one call away in a separate
     *       {@code REQUIRES_NEW}-transactional bean, still marked that transaction
     *       rollback-only before the catch block ever ran - because {@code JpaRepository}'s
     *       own transactional advice intercepts the exception first, and catching it later in
     *       application code cannot undo that. Every "duplicate" ended up throwing
     *       {@code UnexpectedRollbackException} instead of returning a clean {@code false}.</li>
     * </ol>
     * <p>Avoiding the exception entirely - checking the affected-row count instead of catching
     * a failure - sidesteps both problems and is also simpler: the marker and the notification
     * can now share one ordinary transaction, so a genuine failure saving the notification
     * correctly rolls back the marker too, allowing a legitimate future retry instead of
     * permanently marking a never-actually-notified event as done.</p>
     */
    @Transactional
    public boolean recordEventAndNotify(UUID eventId, UUID recipientId, NotificationType type, String payloadJson) {
        if (processedEventRepository.tryInsert(eventId) == 0) {
            log.debug("Event {} already processed - duplicate delivery, skipping", eventId);
            return false;
        }
        notificationRepository.save(new Notification(recipientId, type, payloadJson));
        return true;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMyNotifications(UUID recipientId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable).map(NotificationService::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        notification.markRead();
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        notificationRepository.markAllReadForRecipient(recipientId);
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getPayload(), n.isRead(), n.getCreatedAt());
    }
}
