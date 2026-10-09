package com.dayo.capstone.cadence.common.email;

/**
 * One way of getting an email out. Picked by EMAIL_PROVIDER (EmailSenderConfig):
 * log (default), smtp, brevo-api, resend-api. Swapping provider is a config
 * change only; EmailService is the single entry point callers use.
 */
public interface EmailSender {

    /** Short name for logs, e.g. "brevo-api". */
    String name();

    void send(EmailMessage message) throws EmailSendException;
}
