package com.collabflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @Size(max = 1024, message = "Avatar URL must be at most 1024 characters")
        String avatarUrl) {
}
