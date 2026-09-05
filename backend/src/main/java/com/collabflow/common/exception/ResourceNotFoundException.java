package com.collabflow.common.exception;

/** Thrown when a request references a resource (by id, typically) that does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceType, Object id) {
        return new ResourceNotFoundException(resourceType + " " + id + " was not found");
    }
}
