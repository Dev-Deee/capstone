package com.dayo.capstone.cadence.common.email;

/**
 * A transport failed. {@code retryable} is true for things that may pass on a
 * second try (timeouts, connection errors, 5xx); false for ones that won't
 * (bad API key, rejected address). Messages never include credentials.
 */
public class EmailSendException extends Exception {

    private final boolean retryable;

    public EmailSendException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
