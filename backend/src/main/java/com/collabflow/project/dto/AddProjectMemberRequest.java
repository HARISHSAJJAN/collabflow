package com.collabflow.project.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddProjectMemberRequest(@NotNull(message = "User id is required") UUID userId) {
}
