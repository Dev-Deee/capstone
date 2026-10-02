package com.dayo.capstone.cadence.common.exception;

/**
 * A 403 whose message is safe to show the user (e.g. "This invite was sent to
 * a different email address"). Spring Security's own AccessDeniedException
 * stays a generic 403 so internal permission details aren't exposed.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
