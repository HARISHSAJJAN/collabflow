package com.collabflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProjectRequest(

        @NotNull(message = "Team id is required")
        UUID teamId,

        @NotBlank(message = "Project name is required")
        @Size(max = 255, message = "Project name must be at most 255 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {
}
