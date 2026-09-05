package com.collabflow.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddLabelRequest(
        @NotBlank(message = "Label is required")
        @Size(max = 50, message = "Label must be at most 50 characters")
        String label) {
}
