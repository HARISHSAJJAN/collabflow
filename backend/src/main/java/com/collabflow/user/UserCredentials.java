package com.collabflow.user;

import java.util.UUID;

/**
 * The narrow, deliberately named exception to this module's usual encapsulation: the auth
 * module needs the password hash to decide whether a login attempt succeeds, so this record
 * exposes it - but only this, and only through {@link UserAccountService}, never the
 * underlying entity or repository. No other module has a reason to ever ask for this.
 */
public record UserCredentials(UUID userId, String email, String passwordHash, boolean active) {}
