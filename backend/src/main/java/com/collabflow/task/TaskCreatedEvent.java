package com.collabflow.task;

import java.util.UUID;

/** Consumed by the audit module (Phase 8); also published to Kafka from Phase 10 for other consumers. See docs/architecture.md's "Two kinds of cross-module events." */
public record TaskCreatedEvent(UUID taskId, UUID projectId, String title, UUID createdBy) {
}
