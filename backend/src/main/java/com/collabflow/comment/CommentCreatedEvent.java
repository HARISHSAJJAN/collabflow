package com.collabflow.comment;

import java.util.UUID;

/** Consumed by the audit module (Phase 8) and, from Phase 11, the notification module (task watchers/mentions). See docs/architecture.md's "Two kinds of cross-module events." */
public record CommentCreatedEvent(UUID commentId, UUID taskId, UUID projectId, UUID authorId) {
}
