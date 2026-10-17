package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.common.email.EmailMessage;
import com.dayo.capstone.cadence.common.email.EmailSendException;
import com.dayo.capstone.cadence.common.email.EmailSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/** EmailService's guard rails, with a fake transport and a same-thread executor. */
class EmailServiceTest {

    private static final Executor INLINE = Runnable::run;

    /** Fails the first {@code failures} attempts with the given retryability, then succeeds. */
    static class FakeSender implements EmailSender {
        final List<EmailMessage> sent = new ArrayList<>();
        int attempts = 0;
        int failures;
        boolean retryable;
        RuntimeException crash;

        @Override public String name() { return "fake"; }

        @Override
        public void send(EmailMessage m) throws EmailSendException {
            attempts++;
            if (crash != null) throw crash;
            if (attempts <= failures) throw new EmailSendException("fake failure", retryable, null);
            sent.add(m);
        }
    }

    @Test
    void notificationEmailStopsAtTheDailyCapButAccountEmailContinues() {
        FakeSender sender = new FakeSender();
        EmailService service = new EmailService(sender, INLINE, 2, 1);
        service.sendNotification("a@x.test", "1", "b");
        service.sendNotification("a@x.test", "2", "b");
        service.sendNotification("a@x.test", "3 — over the cap", "b");
        service.send("a@x.test", "Reset your password", "b");
        assertThat(sender.sent).extracting(EmailMessage::subject)
                .containsExactly("1", "2", "Reset your password");
    }

    @Test
    void retryableFailuresAreRetriedABoundedNumberOfTimes() {
        FakeSender sender = new FakeSender();
        sender.failures = 2;
        sender.retryable = true;
        new EmailService(sender, INLINE, 100, 1).send("a@x.test", "s", "b");
        assertThat(sender.attempts).isEqualTo(3);
        assertThat(sender.sent).hasSize(1);

        FakeSender down = new FakeSender();
        down.failures = 99;
        down.retryable = true;
        new EmailService(down, INLINE, 100, 1).send("a@x.test", "s", "b");
        assertThat(down.attempts).isEqualTo(EmailService.MAX_ATTEMPTS);
    }

    @Test
    void permanentFailuresAndCrashesNeverReachTheCaller() {
        FakeSender rejected = new FakeSender();
        rejected.failures = 99;
        rejected.retryable = false;
        new EmailService(rejected, INLINE, 100, 1).send("a@x.test", "s", "b");
        assertThat(rejected.attempts).isEqualTo(1);

        FakeSender crashing = new FakeSender();
        crashing.crash = new IllegalStateException("boom");
        new EmailService(crashing, INLINE, 100, 1).sendNotification("a@x.test", "s", "b"); // must not throw
        assertThat(crashing.attempts).isEqualTo(1);
    }

    @Test
    void aMissingAddressIsSkipped() {
        FakeSender sender = new FakeSender();
        EmailService service = new EmailService(sender, INLINE, 100, 1);
        service.send(null, "s", "b");
        service.sendNotification("  ", "s", "b");
        assertThat(sender.attempts).isZero();
        assertThat(service.sentToday()).isZero();
    }
}
