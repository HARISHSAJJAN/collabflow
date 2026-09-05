package com.collabflow.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user changes their password. This is a plain Spring
 * {@code ApplicationEvent} payload, not a Kafka event - a deliberate distinction from the
 * domain events introduced in Phase 10 (kafka module). The rule of thumb used throughout this
 * codebase: a Kafka event is for something another *bounded context* (or a future genuinely
 * separate service) might care about (a task being assigned, a comment being posted); a plain
 * in-process Spring event is for a side effect purely internal to this one JVM, between two
 * modules that both already live here, where there is no reason to pay for serialization,
 * a broker round trip, or the "what if this consumer is down" questions that come with
 * asynchronous, at-least-once delivery.
 *
 * <p>{@code auth}'s listener uses this to revoke every active session for the account - a
 * password change should not leave old sessions (including a possible attacker's, if the
 * change was prompted by a suspected compromise) still valid.</p>
 */
public record PasswordChangedEvent(UUID userId, Instant changedAt) {
}
