package com.collabflow.team.dto;

import com.collabflow.team.TeamRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Role is required") TeamRole role) {
}
