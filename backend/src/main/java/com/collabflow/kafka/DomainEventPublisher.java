package com.collabflow.kafka;

import com.collabflow.common.TransactionUtils;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * The one place in the codebase that talks to Kafka's producer API. Every domain event a
 * module wants a genuinely external, out-of-process consumer to see (as opposed to a purely
 * in-JVM reaction - see docs/architecture.md's "Two kinds of cross-module events") goes
 * through {@link #publish}.
 *
 * <p><b>Delivery guarantee - stated plainly, not oversold</b>: this is <em>at-least-once,
 * best-effort-after-commit</em>, not exactly-once and not guaranteed. Concretely:</p>
 * <ul>
 *   <li>The publish is deferred to run only after the database transaction that produced the
 *       event has committed ({@code afterCommit}, the same pattern and rationale as
 *       {@code RedisCacheService.evictAfterCommit}) - this prevents "phantom" events for a
 *       database change that was then rolled back.</li>
 *   <li>It does <em>not</em> prevent the opposite gap: if the application crashes after the
 *       database commit but before the deferred publish actually runs, that event is lost
 *       forever - nothing recorded that it needed to be sent. The database and Kafka are two
 *       separate systems with no atomic "commit both or neither" between them here.</li>
 *   <li>The idempotent producer (see {@code application.yml}'s
 *       {@code enable.idempotence: true}) only guarantees no *duplicate* delivery caused by
 *       the producer retrying a send it wasn't sure succeeded - it does nothing for the gap
 *       above, and does nothing for a consumer that crashes after processing but before
 *       committing its own offset (which is a real, separate at-least-once source on the
 *       consumer side - see {@code NotificationEventListener}'s idempotency handling for how
 *       *that* gap is closed).</li>
 * </ul>
 *
 * <p>The gap this class's Javadoc admits to - a lost event on a crash at exactly the wrong
 * moment - is a real, understood limitation, not something masked as "exactly-once." Closing
 * it fully would mean a <b>transactional outbox</b>: write the event to an outbox table in the
 * *same* database transaction as the business change (so it's atomic with it), then have a
 * separate poller (or Debezium-style CDC process) read the outbox and publish to Kafka,
 * retrying indefinitely until it succeeds and only then marking the outbox row sent. That is
 * real additional infrastructure this project does not currently have, called out explicitly
 * as a documented future improvement (see docs/kafka.md) rather than pretended away.</p>
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * @param topic one of {@link KafkaTopics}
     * @param key the aggregate id events about the same entity should share, for partition ordering
     * @param payload the event record (e.g. {@code TaskCreatedEvent}) - serialized to JSON with a Java type header, see application.yml
     */
    public void publish(String topic, String key, Object payload) {
        TransactionUtils.runAfterCommit(() -> sendNow(topic, key, payload));
    }

    private void sendNow(String topic, String key, Object payload) {
        String eventId = UUID.randomUUID().toString();
        var message = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, key)
                .setHeader("eventId", eventId)
                .build();
        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} (eventId={}) to topic {}", payload.getClass().getSimpleName(), eventId, topic, ex);
            } else {
                log.debug("Published {} (eventId={}) to {}-{}@{}", payload.getClass().getSimpleName(), eventId, topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
