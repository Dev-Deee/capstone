package com.dayo.capstone.cadence.common.email;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP via Spring's JavaMailSender — for local development, demos, and hosts
 * that allow outbound SMTP (many free hosting plans block ports 465/587; use
 * an HTTPS API provider there). Connect/read/write timeouts are set in
 * application.properties so a blocked port fails fast instead of hanging.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress, String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public void send(EmailMessage m) throws EmailSendException {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(m.to());
            helper.setSubject(m.subject());
            helper.setText(m.body(), false);
            mailSender.send(message);
        } catch (MailAuthenticationException e) {
            throw new EmailSendException("SMTP authentication failed", false, e);
        } catch (Exception e) {
            throw new EmailSendException("SMTP send failed: " + e.getClass().getSimpleName(), true, e);
        }
    }
}
