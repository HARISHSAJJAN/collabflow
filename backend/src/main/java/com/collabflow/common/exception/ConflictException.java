package com.collabflow.common.exception;

/** Thrown when a request conflicts with the current state of a resource (e.g. duplicate email). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
