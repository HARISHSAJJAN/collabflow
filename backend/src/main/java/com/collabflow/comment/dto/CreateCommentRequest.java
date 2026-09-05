package com.collabflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCommentRequest(
        @NotNull(message = "Task id is required") UUID taskId,
        @NotBlank(message = "Comment body is required")
        @Size(max = 5000, message = "Comment must be at most 5000 characters")
        String body) {
}
