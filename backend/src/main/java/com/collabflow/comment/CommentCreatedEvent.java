package com.collabflow.comment;

import java.util.UUID;

/**
 * Consumed by the audit module (Phase 8) and the notification module (Phase 10-11: comment
 * fan-out and @mention parsing - see {@code notification.internal.CommentEventsListener}).
 * See docs/architecture.md's "Two kinds of cross-module events."
 *
 * <p>Carries {@code body} (added Phase 11) specifically so the mention-parsing consumer
 * doesn't need to re-fetch the comment - the event is self-contained. {@code audit}'s
 * listener, added before this field existed, simply doesn't read it; adding a field to an
 * event record consumers access by accessor (not positionally) is a non-breaking change for
 * every existing consumer.</p>
 */
public record CommentCreatedEvent(UUID commentId, UUID taskId, UUID projectId, UUID authorId, String body) {
}
