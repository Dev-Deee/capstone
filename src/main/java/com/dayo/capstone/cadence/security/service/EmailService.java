package com.dayo.capstone.cadence.security.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Wraps actual sending in a try/catch — if SMTP isn't configured (no
 * credentials in .env yet), the flow still works end to end because the
 * raw link/token is always logged as well. This keeps password reset
 * and email verification testable via Swagger before real SMTP is wired in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void send(String to, String subject, String body) {
        log.info("Email content (always logged for local testing) — to={}, subject={}, body={}",
                to, subject, body);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Email send failed (SMTP likely not configured yet) — " +
                    "flow continues using the logged link/token above. Error: {}", e.getMessage());
        }
    }
}