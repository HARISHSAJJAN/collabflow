# ADR-004: Kafka for asynchronous domain events

## Status
Accepted

## Context

Several actions in this system naturally have more than one interested party: creating a
task should be recorded in the audit trail; assigning a task should notify the assignee;
someone commenting should notify the task's assignee and reporter. The producer of each
action (task, project, team, comment) shouldn't need to know the full list of things that
should happen as a result, or be slowed down waiting for all of them - that's exactly the
problem asynchronous, decoupled event delivery solves.

## Decision

Use **Apache Kafka** for domain events that a genuinely separate consumer needs to react to
out-of-process (today, the `notification` module) - see
[ADR-001](adr/ADR-001-modular-monolith.md) and docs/architecture.md's "Two kinds of
cross-module events" for why this is deliberately *not* used for every cross-module reaction;
a plain in-process Spring event (used by `audit`) is preferred whenever the reaction is purely
internal to this one JVM.

Design choices actually implemented, not just discussed:

- **One topic per aggregate domain** (`collabflow.task-events`, `.project-events`,
  `.team-events`, `.comment-events`), carrying multiple event types each, distinguished by
  payload type - see `KafkaTopics`'s Javadoc for why this beats one topic per event type.
- **Keyed by aggregate id** (the task id, project id, etc.) so every event about the same
  entity lands on the same partition, preserving per-aggregate ordering for any single
  consumer.
- **JSON serialization with Java type headers** (`spring.json.add.type.headers`), a
  deliberate simplification justified by this being one codebase where every producer and
  consumer already shares the same event record classes - see application.yml's comment for
  what a genuinely polyglot system would do instead.
- **Idempotent producer** (`enable.idempotence: true`, `acks: all`) - prevents duplicate
  delivery caused by the producer itself retrying an ambiguous send.
- **At-least-once, not exactly-once** - stated plainly in `DomainEventPublisher`'s Javadoc,
  including the specific gap (a crash between a DB commit and the deferred Kafka publish loses
  that event) that a transactional outbox would close and that this project does not have.
  Consumers handle the resulting possibility of duplicate delivery via idempotent processing
  (see below) - correctness does not depend on delivery being exactly-once.
- **Dead-letter topics** for genuine processing failures (3 retries with backoff, then to
  `<topic>.DLT`) - see `KafkaConsumerConfig`.

## Idempotent consumption: a real bug, found three layers deep

The `notification` module's consumers must handle duplicate delivery correctly - and testing
this (not just asserting it) found not one but two real, sequential bugs before landing on the
final design. **Method: reset the `notification-service` consumer group's offsets to earliest
and let the app reconsume every already-processed message** - a direct, repeatable way to
force redelivery on demand, rather than waiting for a real crash-and-redeliver to happen
naturally.

1. **First attempt**: a `processed_events(event_id PRIMARY KEY)` marker row, inserted via
   `save()` before creating the notification, with the expectation that a duplicate id would
   violate the primary key and throw. It didn't - see "Bug 1" below.
2. **Bug 1 (found by replay)**: `ProcessedEvent`'s `@Id` is assigned by application code, not
   `@GeneratedValue`. Spring Data JPA's default `isNew()` check treats any entity with a
   non-null id as *not new*, so `save()` silently called `EntityManager.merge()` (an upsert)
   instead of `persist()` (an insert) - a duplicate id was never rejected, it was just quietly
   re-saved. Fixed by implementing `Persistable<UUID>` with an explicit `isNew()` (see
   `ProcessedEvent`'s Javadoc) - now `save()` genuinely attempts an insert.
3. **Bug 2 (found by replay, again, after fixing Bug 1)**: with a real insert now happening,
   a genuine duplicate correctly threw `DataIntegrityViolationException` - caught inside a
   dedicated `REQUIRES_NEW`-transactional method, one call away from the caller, the same
   pattern used successfully for two earlier propagation bugs (Phases 3-4). This time it
   wasn't enough: `JpaRepository`'s own transactional advice intercepted the exception and
   marked *that* transaction rollback-only before the catch block ever ran, so every
   "duplicate" threw `UnexpectedRollbackException` instead of returning a clean `false`
   (redirecting every redelivered message toward the dead-letter topic - not incorrect, but
   not the intended graceful handling either, and not something you'd want for routine,
   expected redeliveries).
4. **Final fix**: replace the catch-an-exception check with an atomic, exception-free
   `INSERT ... ON CONFLICT DO NOTHING` (`ProcessedEventRepository.tryInsert`), checking the
   affected-row count instead of catching a failure. No exception, no transaction ever marked
   rollback-only, and the marker and the notification now share one ordinary transaction -
   simpler *and* more correct than either previous attempt.

Full details: `NotificationService.recordEventAndNotify`'s Javadoc and
docs/troubleshooting.md.

## Alternatives considered

### RabbitMQ / another message broker
A reasonable alternative for the pub-sub/queue need alone. Kafka was chosen because its
partition-and-offset model, consumer groups, and log-based retention are specifically what
this project's brief asks to be demonstrated (topic, partition, offset, consumer group), and
because it's the more common choice for event-driven architectures at the scale this project
is designed to be discussed at.

### In-process events for everything (no Kafka at all)
Rejected - see ADR-001: it wouldn't demonstrate the distributed-systems concepts (delivery
guarantees, idempotent consumption, partitioning) that are an explicit goal of this project,
and it wouldn't survive `notification` ever being extracted into its own service.

## Consequences

**Positive**
- Producers (task, project, team, comment) don't know or care who's listening - `audit` and
  `notification` were both added without changing any producer's publish call.
- Consumer idempotency is real, tested, and fixed twice over - not just asserted.

**Negative / trade-offs accepted**
- At-least-once delivery with a real (if narrow) gap between a DB commit and a lost event on
  an ill-timed crash - see `DomainEventPublisher`'s Javadoc and the "transactional outbox"
  future improvement.
- Local development now requires a running Kafka broker for the full feature set - mitigated
  by `docker-compose.yml`'s single-broker KRaft setup (see ADR-001) needing no separate
  Zookeeper container.

## Interviewer questions this ADR should let you answer
- "What's your Kafka delivery guarantee, actually?"
- "How do you handle a duplicate/redelivered message?"
- "Walk me through a bug you found in your idempotent-consumer logic."
- "Why one topic per domain instead of one per event type?"
