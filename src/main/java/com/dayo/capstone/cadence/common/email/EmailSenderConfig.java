package com.dayo.capstone.cadence.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

/**
 * Picks the EmailSender from EMAIL_PROVIDER. Everything — provider, keys,
 * SMTP account, sender — comes from the environment (see the README's Email
 * section). A provider chosen without what it needs doesn't silently drop
 * mail: it falls back to the log sender with a loud WARN at startup.
 */
@Slf4j
@Configuration
public class EmailSenderConfig {

    @Bean
    public EmailSender emailSender(
            @Value("${app.email.provider:log}") String provider,
            @Value("${app.email.from-address:}") String fromAddress,
            @Value("${app.email.from-name:Cadence}") String fromName,
            @Value("${app.email.api-key:}") String apiKey,
            @Value("${app.email.api-base-url:}") String apiBaseUrl,
            @Value("${app.email.timeout-ms:5000}") long timeoutMs,
            @Value("${spring.mail.username:}") String smtpUsername,
            JavaMailSender javaMailSender) {
        return choose(provider, fromAddress, fromName, apiKey, apiBaseUrl, Duration.ofMillis(timeoutMs),
                smtpUsername, javaMailSender);
    }

    static EmailSender choose(String provider, String fromAddress, String fromName, String apiKey, String apiBaseUrl,
                              Duration timeout, String smtpUsername, JavaMailSender javaMailSender) {
        String p = provider == null ? "log" : provider.trim().toLowerCase();
        EmailSender sender = switch (p) {
            case "smtp" -> {
                String from = blank(fromAddress) ? smtpUsername : fromAddress;
                if (blank(smtpUsername) || blank(from)) yield fallback(p, "MAIL_USERNAME (and MAIL_PASSWORD, MAIL_HOST) are not set");
                yield new SmtpEmailSender(javaMailSender, from, fromName);
            }
            case "brevo-api" -> blank(apiKey) || blank(fromAddress)
                    ? fallback(p, "EMAIL_API_KEY and EMAIL_FROM_ADDRESS are required")
                    : new BrevoApiEmailSender(blank(apiBaseUrl) ? BrevoApiEmailSender.DEFAULT_BASE_URL : apiBaseUrl,
                            apiKey, fromAddress, fromName, timeout);
            case "resend-api" -> blank(apiKey) || blank(fromAddress)
                    ? fallback(p, "EMAIL_API_KEY and EMAIL_FROM_ADDRESS are required")
                    : new ResendApiEmailSender(blank(apiBaseUrl) ? ResendApiEmailSender.DEFAULT_BASE_URL : apiBaseUrl,
                            apiKey, fromAddress, fromName, timeout);
            case "log" -> new LogEmailSender();
            default -> fallback(p, "unknown provider (use log, smtp, brevo-api or resend-api)");
        };
        log.info("Email provider: {}", sender.name());
        return sender;
    }

    private static EmailSender fallback(String provider, String why) {
        log.warn("EMAIL_PROVIDER={} can't be used: {}. NO EMAIL WILL BE SENT — falling back to the log provider.", provider, why);
        return new LogEmailSender();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
