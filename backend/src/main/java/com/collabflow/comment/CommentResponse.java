package com.collabflow.comment;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID taskId,
        UUID authorId,
        String authorName,
        String body,
        boolean edited,
        Instant createdAt,
        Instant updatedAt) {
}
