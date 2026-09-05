package com.collabflow.task.dto;

import com.collabflow.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull(message = "Status is required") TaskStatus status) {
}
