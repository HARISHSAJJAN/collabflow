# Kafka

Rationale for using Kafka at all, and the idempotent-consumer bugs found while building this
phase: [ADR-004](adr/ADR-004-kafka.md). This document covers the mechanics: producer,
consumer, topics, partitions, offsets, consumer groups, serialization, error handling, and
the actual (not aspirational) delivery guarantee.

## Topics

| Topic | Carries | Key |
|---|---|---|
| `collabflow.task-events` | `TaskCreatedEvent`, `TaskAssignedEvent`, `TaskStatusChangedEvent`, `TaskPriorityChangedEvent` | task id |
| `collabflow.project-events` | `ProjectCreatedEvent` | project id |
| `collabflow.team-events` | `MemberAddedEvent`, `MemberRemovedEvent` | team id |
| `collabflow.comment-events` | `CommentCreatedEvent` | task id |

One topic per aggregate domain, not per event type - see `KafkaTopics`'s Javadoc. Each topic
has a single partition in this local/dev setup (`docker-compose.yml`'s single-broker KRaft
cluster); `docs/architecture.md`'s scaling section covers what changes with more partitions
and a multi-broker cluster.

## Producer

`DomainEventPublisher` is the only class that calls Kafka's producer API. Every publish is
deferred to run **after** the originating database transaction commits (`afterCommit`, the
same pattern as `RedisCacheService.evictAfterCommit`) - see its Javadoc for the delivery
guarantee this does and does not provide, including the specific gap (a lost event on a crash
between commit and publish) that a transactional outbox would close, which this project does
not have.

Configuration (`application.yml`): `acks: all`, `enable.idempotence: true`, `retries: 5` -
the idempotent producer prevents *duplicate* delivery caused by the producer's own retry
logic being unsure whether an earlier send succeeded. It does nothing for the gap above, and
nothing for duplicate delivery caused by consumer-side redelivery (a different, also-real
source of duplicates - see "Duplicate events" below).

## Consumer groups

All current consumers share one group id, `notification-service` (`TaskEventsListener`,
`CommentEventsListener`, `TeamEventsListener`, all in the `notification` module). A consumer
group is Kafka's unit of "one logical subscriber" - every member of a group divides up a
topic's partitions between them (so a group with more consumers than partitions has idle
members), and the group as a whole gets its own independently-tracked offset per partition.
`audit`'s consumption of the *same* underlying actions happens through the in-process Spring
event path instead (see docs/architecture.md), not a second Kafka consumer group - there's
currently only one Kafka consumer group in this application. A hypothetical future analytics
consumer would get its **own** group id, so it would see every message independently of
`notification-service`'s progress through the log.

## Serialization

JSON via `spring-kafka`'s `JsonSerializer`/`JsonDeserializer`, with
`spring.json.add.type.headers: true` - a `__TypeId__` header carries the exact Java class
name, letting `@KafkaHandler`-based dispatch (see `TaskEventsListener`) route each payload to
the right method without a separate discriminator field. This is a deliberate simplification
specific to being one codebase where every producer and consumer shares the same event record
classes - see `application.yml`'s comment for what a genuinely polyglot system (or one where
producer and consumer evolve independently) would use instead (a schema registry, or an
explicit `eventType` string field).

## Error handling, retries, and dead letters

`KafkaConsumerConfig` registers one `CommonErrorHandler` for every listener: on an uncaught
exception, retry up to 3 times with a 1-second delay (`FixedBackOff`), then publish the
message to a dead-letter topic (`<original-topic>.DLT`) and move on - so one poison message
can't block a partition forever. **Nothing currently reads the `.DLT` topics** - this is a
real, acknowledged gap (a real system would alert on and eventually reprocess or manually
inspect dead-lettered messages), not a hidden one.

## Duplicate events and idempotency

At-least-once delivery makes redelivery of an already-processed message a normal, expected
occurrence (a consumer crash after processing but before committing its offset, a rebalance,
or - as used to test this - a deliberate offset reset). `notification`'s consumers must
therefore make creating a notification idempotent per event.

**How, after two wrong turns found by literally replaying already-processed messages** (see
ADR-004 for the full story): a `processed_events(event_id PRIMARY KEY)` table, and an atomic
`INSERT ... ON CONFLICT DO NOTHING` (`ProcessedEventRepository.tryInsert`) checked *before*
creating the notification, in the same transaction. If the insert affects zero rows, the
event was already processed - return early, no second notification. No exception is ever
thrown for the ordinary "yes, this is a duplicate" case, which is what makes this safe to
share a transaction with the notification write itself (both commit together, or both roll
back together on a genuine failure).

**Fan-out gets its own idempotency key per recipient**: `CommentCreatedEvent` notifies
multiple people (assignee and reporter) from one Kafka message, which shares one `eventId`.
Using that raw `eventId` as the dedup key for every recipient would let only the *first*
recipient's insert succeed. `CommentEventsListener` derives a stable, per-recipient key
instead (`UUID.nameUUIDFromBytes(eventId + ":" + recipientId)`), so each recipient's
notification is independently idempotent without losing the fan-out.

## Ordering

Kafka only guarantees order *within a partition*. Since every event is keyed by its aggregate
id, every event about the same task (say) always lands on the same partition and is therefore
delivered to any one consumer in the order it was produced. There is **no ordering guarantee
across different aggregates** (a `TaskCreatedEvent` for task A and a `MemberAddedEvent` for an
unrelated team have no defined relative order) - which is fine, since nothing in this system
needs cross-aggregate ordering.

## What's not implemented, on purpose

- **Transactional outbox**: closing the "lost event on an ill-timed crash" gap in
  `DomainEventPublisher`'s guarantee. Real additional infrastructure (an outbox table plus a
  poller/CDC process), not built for this project's scope - see ADR-004.
- **Dead-letter topic consumption**: messages that exhaust retries land in `.DLT` topics that
  nothing currently reads.
- **A second, independent consumer group** (e.g. analytics): the architecture supports this
  trivially (a new group id sees every message from the start of retention, independent of
  `notification-service`), it just hasn't been built because nothing needs it yet.
