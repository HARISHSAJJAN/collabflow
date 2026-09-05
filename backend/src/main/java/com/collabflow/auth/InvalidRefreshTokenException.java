package com.collabflow.auth;

import org.springframework.security.core.AuthenticationException;

/** Covers a missing, expired, or already-used (rotated) refresh token. Maps to HTTP 401, same as any other authentication failure. */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
