package com.collabflow.project;

import com.collabflow.team.TeamRole;
import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        TeamRole teamRole,
        Instant addedAt) {
}
