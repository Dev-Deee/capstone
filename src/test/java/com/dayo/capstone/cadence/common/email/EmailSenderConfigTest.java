package com.dayo.capstone.cadence.common.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** EMAIL_PROVIDER picks the transport; a provider missing what it needs falls back to log, never drops mail silently. */
class EmailSenderConfigTest {

    private static final Duration T = Duration.ofSeconds(1);
    private final JavaMailSenderImpl smtp = new JavaMailSenderImpl();

    @Test
    void defaultsToLog() {
        assertThat(EmailSenderConfig.choose(null, "", "Cadence", "", "", "", T, "", smtp)).isInstanceOf(LogEmailSender.class);
        assertThat(EmailSenderConfig.choose("log", "", "Cadence", "", "", "", T, "", smtp)).isInstanceOf(LogEmailSender.class);
    }

    @Test
    void picksEachConfiguredProvider() {
        assertThat(EmailSenderConfig.choose("brevo-api", "a@b.test", "Cadence", "key", "", "", T, "", smtp))
                .isInstanceOf(BrevoApiEmailSender.class);
        assertThat(EmailSenderConfig.choose("RESEND-API", "a@b.test", "Cadence", "key", "", "", T, "", smtp))
                .isInstanceOf(ResendApiEmailSender.class);
        assertThat(EmailSenderConfig.choose("smtp", "", "Cadence", "", "", "", T, "me@gmail.test", smtp))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void mailjetNeedsBothKeysAndASender() {
        assertThat(EmailSenderConfig.choose("mailjet-api", "a@b.test", "Cadence", "key", "secret", "", T, "", smtp))
                .isInstanceOf(MailjetApiEmailSender.class);
        assertThat(EmailSenderConfig.choose("mailjet-api", "a@b.test", "Cadence", "key", "", "", T, "", smtp))
                .isInstanceOf(LogEmailSender.class);
        assertThat(EmailSenderConfig.choose("mailjet-api", "", "Cadence", "key", "secret", "", T, "", smtp))
                .isInstanceOf(LogEmailSender.class);
    }

    @Test
    void misconfiguredProvidersFallBackToLog() {
        assertThat(EmailSenderConfig.choose("brevo-api", "a@b.test", "Cadence", "", "", "", T, "", smtp)).isInstanceOf(LogEmailSender.class);
        assertThat(EmailSenderConfig.choose("resend-api", "", "Cadence", "key", "", "", T, "", smtp)).isInstanceOf(LogEmailSender.class);
        assertThat(EmailSenderConfig.choose("smtp", "", "Cadence", "", "", "", T, "", smtp)).isInstanceOf(LogEmailSender.class);
        assertThat(EmailSenderConfig.choose("carrier-pigeon", "a@b.test", "Cadence", "key", "", "", T, "", smtp)).isInstanceOf(LogEmailSender.class);
    }
}
