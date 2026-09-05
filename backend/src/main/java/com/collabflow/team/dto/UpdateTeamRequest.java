package com.collabflow.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(

        @NotBlank(message = "Team name is required")
        @Size(max = 255, message = "Team name must be at most 255 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {
}
