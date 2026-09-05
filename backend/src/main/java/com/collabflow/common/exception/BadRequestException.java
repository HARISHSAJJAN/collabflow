package com.collabflow.common.exception;

/**
 * For request-shape problems that Bean Validation can't express (e.g. "either projectId or
 * teamId must be given, but not neither"), as opposed to a single field failing a constraint.
 * Maps to HTTP 400, same as a validation failure.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
