package com.collabflow.common.web;

import java.time.Instant;
import java.util.List;

/**
 * The single error response shape returned by every endpoint in this API. A client should
 * never need to guess whether an error looks like this one or that one - it always looks like
 * this, whether it's a validation failure, a missing resource, or an unhandled exception.
 *
 * @param fieldErrors populated only for validation failures (400); {@code null} otherwise.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError ofValidation(String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), 400, "VALIDATION_ERROR", message, path, fieldErrors);
    }
}
