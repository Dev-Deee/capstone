package com.dayo.capstone.cadence.common.exception;

/** A 409: the request is valid but clashes with existing state (e.g. a taken slug). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
