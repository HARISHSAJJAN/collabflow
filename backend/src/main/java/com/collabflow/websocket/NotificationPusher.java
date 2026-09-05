package com.collabflow.websocket;

import com.collabflow.common.TransactionUtils;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Used by {@code notification.NotificationService} to push a newly created notification to
 * its recipient in real time, on top of the existing "poll {@code GET /api/v1/notifications}"
 * path - the REST API keeps working exactly as before (e.g. for a client that hasn't opened a
 * WebSocket connection at all), this is purely additive.
 *
 * <p>Relies on Spring's user-destination machinery: {@code convertAndSendToUser(userId, ...)}
 * resolves to the specific STOMP session(s) whose principal name (set by
 * {@link JwtStompAuthInterceptor} at {@code CONNECT}) equals {@code userId.toString()}. If
 * that user has no open WebSocket session right now, this is a silent no-op - they'll simply
 * see the notification next time they load it over REST. There is no delivery guarantee here
 * at all (unlike Kafka's at-least-once) - this is a best-effort, "if you're connected right
 * now" push, which is the correct expectation for a live UI badge, not a source of truth.</p>
 */
@Component
public class NotificationPusher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPusher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationPusher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void pushToUser(UUID userId, Object payload) {
        TransactionUtils.runAfterCommit(() -> {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", payload);
            log.debug("Pushed {} to user {}", payload.getClass().getSimpleName(), userId);
        });
    }
}
