package com.collabflow.common.exception;

/**
 * Thrown by business logic when an authenticated user is not allowed to perform a specific
 * operation on a specific resource (e.g. a MEMBER trying to delete a team). Distinct from
 * Spring Security's {@code AccessDeniedException}, which covers coarser, endpoint-level
 * authorization - this is for authorization decisions that depend on business data (whose
 * team is this, what role does this user hold in it) and so can only be evaluated inside the
 * service layer. Maps to HTTP 403.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
