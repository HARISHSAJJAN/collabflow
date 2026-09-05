package com.collabflow.common.exception;

/** Thrown when a caller has exceeded a rate limit on a sensitive endpoint. Maps to HTTP 429. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
