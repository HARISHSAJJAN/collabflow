package com.collabflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.AbstractIntegrationTest;
import com.collabflow.TestFixtures;
import com.collabflow.TestFixtures.RegisteredUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * A regression guard for the two real bugs found in Phase 10 (see ADR-004 and
 * docs/troubleshooting.md): a manually-assigned {@code @Id} that made {@code save()} silently
 * upsert instead of insert, then a transaction-rollback-poisoning bug in the fix for that.
 * Both were found by literally replaying Kafka messages against a running application - this
 * test exercises the same underlying idempotency guarantee
 * ({@code NotificationService.recordEventAndNotify}) directly, at the service layer, rather
 * than through a real Kafka round-trip: the property being guarded ("the same event id never
 * produces two notifications") lives entirely in that one method, so testing it there is both
 * sufficient and much faster than standing up a producer/consumer/broker round trip for what
 * is, underneath the Kafka plumbing, a database idempotency question.
 */
class NotificationIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    void theSameEventIdNeverProducesTwoNotifications() {
        RegisteredUser recipient = TestFixtures.registerAndLogin(restTemplate, baseUrl, "recipient");
        UUID eventId = UUID.randomUUID();

        boolean first = notificationService.recordEventAndNotify(
                eventId, recipient.userId(), NotificationType.TASK_ASSIGNED, "{\"taskId\":\"" + UUID.randomUUID() + "\"}");
        boolean secondDeliveryOfTheSameEvent = notificationService.recordEventAndNotify(
                eventId, recipient.userId(), NotificationType.TASK_ASSIGNED, "{\"taskId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(first).isTrue();
        assertThat(secondDeliveryOfTheSameEvent).isFalse();

        var page = notificationService.listMyNotifications(recipient.userId(), PageRequest.of(0, 20));
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void replayingTenDuplicatesStillLeavesExactlyOneNotification() {
        // The exact scenario the Phase 10 bugs were caught with: not a single retry, but a
        // full backlog replay (in production, this is what re-consuming from an earlier
        // offset after a consumer restart looks like).
        RegisteredUser recipient = TestFixtures.registerAndLogin(restTemplate, baseUrl, "recipient");
        UUID eventId = UUID.randomUUID();

        int successes = 0;
        for (int i = 0; i < 10; i++) {
            boolean created = notificationService.recordEventAndNotify(
                    eventId, recipient.userId(), NotificationType.COMMENT_ADDED, "{\"replay\":" + i + "}");
            if (created) {
                successes++;
            }
        }

        assertThat(successes).isEqualTo(1);
        var page = notificationService.listMyNotifications(recipient.userId(), PageRequest.of(0, 20));
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void differentEventIdsForTheSameRecipientEachProduceTheirOwnNotification() {
        RegisteredUser recipient = TestFixtures.registerAndLogin(restTemplate, baseUrl, "recipient");

        notificationService.recordEventAndNotify(UUID.randomUUID(), recipient.userId(), NotificationType.TASK_ASSIGNED, "{}");
        notificationService.recordEventAndNotify(UUID.randomUUID(), recipient.userId(), NotificationType.COMMENT_ADDED, "{}");

        var page = notificationService.listMyNotifications(recipient.userId(), PageRequest.of(0, 20));
        assertThat(page.totalElements()).isEqualTo(2);
    }
}
