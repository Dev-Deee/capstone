package com.dayo.capstone.cadence.notification;

import com.dayo.capstone.cadence.common.event.DomainEvent;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.StudentCohortChangedEvent;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.notification.service.PushGateway;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/** No VAPID keys (the default): push is off, subscribing is refused, and in-app delivery carries on. */
class PushDisabledTest extends IntegrationTestBase {

    @MockitoBean PushGateway gateway;
    @Autowired NotificationRepository notificationRepository;
    @Autowired DomainEvents domainEvents;
    @Autowired TransactionTemplate tx;

    @Test
    void pushIsOffAndNotificationsStillArrive() throws Exception {
        Institution inst = institution("No Push Academy");
        User student = user("nopush");
        member(student, inst, Role.STUDENT);
        String token = login(student);

        Resp key = get("/api/push/public-key", token);
        assertThat(key.status()).isEqualTo(200);
        assertThat(key.body().get("enabled").asBoolean()).isFalse();
        assertThat(key.text("publicKey")).isNull();

        Map<String, Object> sub = Map.of("endpoint", "https://push.example.test/send/" + UUID.randomUUID(),
                "keys", Map.of("p256dh", "x", "auth", "y"));
        assertThat(call("POST", "/api/push/subscriptions", sub, token).status()).isEqualTo(409);

        tx.executeWithoutResult(s -> domainEvents.publish((DomainEvent)
                new StudentCohortChangedEvent(inst.getId(), null, student.getId(), null, "Cohort Quiet", UUID.randomUUID())));

        long deadline = System.currentTimeMillis() + 5000;
        while (get("/api/notifications/unread-count", token).body().get("count").asInt() == 0) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("notification never arrived");
            Thread.sleep(50);
        }
        Thread.sleep(200); // give a (wrongly) scheduled push time to run before checking it didn't
        verifyNoInteractions(gateway);
    }
}
