-- Notifications (notification module) and the Kafka-consumer idempotency ledger.
--
-- Built in Phase 10 alongside Kafka, ahead of the brief's own "Phase 11: Notifications" -
-- see docs/kafka.md for why: Kafka's consumer side needs a real, meaningful consumer to
-- actually demonstrate consumption (offsets, consumer groups, idempotent processing), and
-- "create a notification when a Kafka event arrives" is that consumer. Phase 11 in the
-- development log below covers what's still added on top of this - due-date-approaching
-- notifications (needs a scheduled job, not an event) and any remaining API polish; Phase 12
-- adds real-time WebSocket delivery on top of what's already being created here.
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(50) NOT NULL,
    payload      JSONB NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "My notifications" (newest first) and "my unread count" are the only two queries this table
-- serves. The partial index (unread only) keeps the unread-count query cheap even as a user
-- accumulates a long read history over time.
CREATE INDEX ix_notifications_recipient_created_at ON notifications (recipient_id, created_at DESC);
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_id) WHERE is_read = FALSE;

-- The idempotency ledger for Kafka consumption: before acting on an event, the consumer tries
-- to INSERT the event's id here first, in the SAME transaction as creating the resulting
-- notification (or whatever other side effect). If that insert violates the primary key (the
-- event was already processed - a redelivery, which at-least-once delivery makes a normal,
-- expected occurrence, not an error condition), the transaction is rolled back and the event
-- is treated as a no-op duplicate rather than double-processed. See
-- NotificationEventListener's Javadoc and docs/kafka.md's "duplicate events / idempotency"
-- section.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
