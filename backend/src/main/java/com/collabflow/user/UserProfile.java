package com.collabflow.user;

import java.time.Instant;
import java.util.UUID;

/**
 * The full "view my profile" shape - richer than {@link UserSummary}, which is what gets
 * handed to other modules for display purposes (a comment byline, an assignee dropdown).
 * {@code UserProfile} is only ever returned to the user themselves, via {@code /users/me}.
 */
public record UserProfile(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        Instant lastLoginAt,
        Instant createdAt) {
}
