package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.common.email.EmailMessage;
import com.dayo.capstone.cadence.common.email.EmailSendException;
import com.dayo.capstone.cadence.common.email.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.Executor;

/**
 * The single entry point for outgoing email. Which transport actually sends
 * it (log / smtp / brevo-api / resend-api) is the EmailSender chosen by
 * EMAIL_PROVIDER — callers never know.
 *
 * - Sending is asynchronous on the mail executor, so a slow or blocked
 *   provider never holds up the request that triggered it.
 * - Transport failures are retried a bounded number of times when they might
 *   pass (timeouts, 5xx), logged with the provider name, never with
 *   credentials, and never surface to the user.
 * - A daily counter guards the provider's free-tier cap: a WARN as it nears
 *   the cap; past it, notification emails are skipped (in-app delivery is
 *   unaffected) while account emails — verification, password reset, invites
 *   — are still attempted.
 * - Email bodies carry sign-in and reset links, so only the log provider ever
 *   writes a body to the logs.
 */
@Slf4j
@Service
public class EmailService {

    static final int MAX_ATTEMPTS = 3;

    private final EmailSender sender;
    private final Executor mailExecutor;
    private final int dailyCap;
    private final long retryBackoffMs;

    private LocalDate counterDay = LocalDate.now();
    private int sentToday = 0;
    private boolean warnedToday = false;

    public EmailService(EmailSender sender,
                        @Qualifier("mailExecutor") Executor mailExecutor,
                        @Value("${app.email.daily-cap:280}") int dailyCap,
                        @Value("${app.email.retry-backoff-ms:1000}") long retryBackoffMs) {
        this.sender = sender;
        this.mailExecutor = mailExecutor;
        this.dailyCap = dailyCap;
        this.retryBackoffMs = retryBackoffMs;
    }

    /** Account email (verification, password reset, invites): always attempted. */
    public void send(String to, String subject, String body) {
        dispatch(new EmailMessage(to, subject, body), true);
    }

    /** Notification email: best effort, skipped once today's cap is reached. */
    public void sendNotification(String to, String subject, String body) {
        dispatch(new EmailMessage(to, subject, body), false);
    }

    private void dispatch(EmailMessage message, boolean essential) {
        if (message.to() == null || message.to().isBlank()) {
            log.warn("Email not sent: no recipient address (subject={})", message.subject());
            return;
        }
        if (!reserve(essential)) {
            log.warn("Email skipped: daily cap of {} reached — notification stays in-app only (subject={})",
                    dailyCap, message.subject());
            return;
        }
        mailExecutor.execute(() -> deliver(message));
    }

    // Counts a send against today's cap. False = skip (only ever for non-essential mail).
    private synchronized boolean reserve(boolean essential) {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDay)) {
            counterDay = today;
            sentToday = 0;
            warnedToday = false;
        }
        if (sentToday >= dailyCap && !essential) return false;
        sentToday++;
        if (!warnedToday && sentToday >= dailyCap * 0.8) {
            warnedToday = true;
            log.warn("Email volume at {} of the daily cap of {} — notification emails stop at the cap; "
                    + "account emails continue but may be refused by the provider", sentToday, dailyCap);
        }
        return true;
    }

    private void deliver(EmailMessage message) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                sender.send(message);
                log.info("Email sent: provider={}, to={}, subject={}", sender.name(), message.to(), message.subject());
                return;
            } catch (EmailSendException e) {
                boolean retry = e.isRetryable() && attempt < MAX_ATTEMPTS;
                log.error("Email send failed: provider={}, to={}, attempt={}/{}, reason={}{}",
                        sender.name(), message.to(), attempt, MAX_ATTEMPTS, e.getMessage(), retry ? " — retrying" : "");
                if (!retry) return;
                sleep(retryBackoffMs * attempt);
            } catch (RuntimeException e) {
                log.error("Email send failed unexpectedly: provider={}, to={}", sender.name(), message.to(), e);
                return;
            }
        }
    }

    // Package-private so tests can read the counter.
    synchronized int sentToday() {
        return sentToday;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
