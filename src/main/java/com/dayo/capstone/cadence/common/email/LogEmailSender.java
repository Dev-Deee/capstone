package com.dayo.capstone.cadence.common.email;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends nothing: logs the message, links and all, so local development works
 * without a mail account. The only sender that ever logs an email body —
 * real ones carry sign-in and reset links that must not end up in logs.
 */
@Slf4j
public class LogEmailSender implements EmailSender {

    @Override
    public String name() {
        return "log";
    }

    @Override
    public void send(EmailMessage m) {
        log.info("Email (log provider — not sent) — to={}, subject={}, body={}", m.to(), m.subject(), m.body());
    }
}
