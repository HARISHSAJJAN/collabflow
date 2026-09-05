package com.collabflow.user;

import java.util.UUID;

/**
 * A user's public-within-the-app display information: what a team roster, an assignee
 * dropdown, or a comment author byline needs. Never includes the password hash - contrast
 * with {@link UserCredentials}, which exists for exactly one caller (auth, during login) and
 * is never handed out for display purposes.
 */
public record UserSummary(UUID id, String email, String fullName, String avatarUrl) {}
