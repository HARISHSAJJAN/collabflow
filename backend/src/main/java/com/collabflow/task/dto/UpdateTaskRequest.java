package com.collabflow.task.dto;

import com.collabflow.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTaskRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        TaskPriority priority,

        LocalDate dueDate) {
}
