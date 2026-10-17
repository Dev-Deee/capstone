package com.dayo.capstone.cadence.notification;

import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.InviteAcceptedEvent;
import com.dayo.capstone.cadence.common.event.StudentCohortChangedEvent;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.notification.config.NotificationCleanupJob;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.dayo.capstone.cadence.security.service.EmailService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Stage 4: domain events → in-app notifications. Listeners are asynchronous
 * and run after commit, so assertions wait for them (await) and absence is
 * checked only after a positive control has arrived.
 */
@Import(NotificationBackboneTest.ThrowingListenerConfig.class)
class NotificationBackboneTest extends IntegrationTestBase {

    /** A second listener on the same event that always fails — must not affect the producer or other listeners. */
    @TestConfiguration
    static class ThrowingListenerConfig {
        @Bean
        ThrowingListener throwingListener() {
            return new ThrowingListener();
        }
    }

    static class ThrowingListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(StudentCohortChangedEvent e) {
            throw new IllegalStateException("listener failure (expected in tests)");
        }
    }

    @Autowired NotificationRepository notificationRepository;
    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired DomainEvents domainEvents;
    @Autowired TransactionTemplate tx;
    @Autowired NotificationCleanupJob cleanupJob;
    @MockitoSpyBean EmailService emailService;

    Institution inst;
    Cohort cohort;
    User student;

    @BeforeEach
    void setUp() {
        inst = institution("Notify Academy");
        cohort = cohort(inst);
        student = user("stu");
        member(student, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(student);
        e.setCohort(cohort);
        e.setEnrolledAt(LocalDateTime.now().minusDays(1));
        enrollmentRepository.save(e);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("Timed out waiting for the listener");
    }

    private List<Notification> notificationsOf(User u) {
        return tx.execute(s -> notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(u.getId()))
                .peek(n -> n.getInstitution().getId())
                .toList());
    }

    private void publishCommitted(Object event) {
        tx.executeWithoutResult(s -> domainEvents.publish((com.dayo.capstone.cadence.common.event.DomainEvent) event));
    }

    private StudentCohortChangedEvent cohortEvent(Institution i, User who, String to) {
        return new StudentCohortChangedEvent(i.getId(), null, who.getId(), null, to, UUID.randomUUID());
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void manualOverrideNotifiesTheStudentWhoCanListAndMarkItRead() {
        User fac = user("fac");
        member(fac, inst, Role.FACILITATOR);
        Session session = session(cohort, subject(inst), fac);

        Resp r = call("PUT", "/api/sessions/" + session.getPublicId() + "/attendance/" + student.getPublicId(),
                Map.of("status", "LATE", "reason", "Arrived after the code changed"), login(fac));
        assertThat(r.status()).isEqualTo(200);
        await(() -> !notificationsOf(student).isEmpty());

        String token = login(student);
        Resp list = get("/api/notifications", token);
        assertThat(list.body().get("items").size()).isEqualTo(1);
        var item = list.body().get("items").get(0);
        assertThat(item.get("type").asText()).isEqualTo("ATTENDANCE_OVERRIDDEN");
        assertThat(item.get("body").asText()).contains(session.getSubject().getName()).contains("Late");
        assertThat(item.get("linkRoute").asText()).isEqualTo("/dashboard/student/learning?tab=attendance");
        assertThat(item.get("read").asBoolean()).isFalse();
        assertThat(get("/api/notifications/unread-count", token).body().get("count").asInt()).isEqualTo(1);

        assertThat(call("PATCH", "/api/notifications/" + item.get("publicId").asText() + "/read", null, token).status())
                .isEqualTo(200);
        assertThat(get("/api/notifications/unread-count", token).body().get("count").asInt()).isZero();
        assertThat(notificationsOf(fac)).isEmpty(); // the actor isn't notified of their own action
        // ATTENDANCE_OVERRIDDEN is on the email whitelist.
        verify(emailService, timeout(2000)).sendNotification(eq(student.getEmail()), eq("Attendance updated"), anyString());
    }

    @Test
    void typesOffTheWhitelistAreInAppOnly() {
        publishCommitted(cohortEvent(inst, student, "In-app only"));
        await(() -> !notificationsOf(student).isEmpty());
        verify(emailService, never()).sendNotification(eq(student.getEmail()), anyString(), anyString());
    }

    @Test
    void aRolledBackTransactionEmitsNothing() {
        tx.executeWithoutResult(s -> {
            domainEvents.publish(cohortEvent(inst, student, "Rolled back"));
            s.setRollbackOnly();
        });
        publishCommitted(cohortEvent(inst, student, "Committed"));

        await(() -> notificationsOf(student).stream().anyMatch(n -> n.getTitle().contains("Committed")));
        assertThat(notificationsOf(student)).noneMatch(n -> n.getTitle().contains("Rolled back"));
    }

    @Test
    void aFailingListenerDoesntAffectTheProducerOrOtherListeners() {
        // ThrowingListener fails on every StudentCohortChangedEvent.
        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        Cohort other = cohort(inst);
        Resp moved = call("POST", "/api/cohorts/" + other.getPublicId() + "/enrollments",
                Map.of("studentPublicId", student.getPublicId().toString()), login(owner));
        assertThat(moved.status()).isBetween(200, 201);
        await(() -> notificationsOf(student).stream().anyMatch(n -> n.getType() == NotificationType.COHORT_CHANGED));
        assertThat(notificationsOf(student).get(0).getBody()).contains(cohort.getName()).contains(other.getName());
    }

    @Test
    void aDuplicateDeliveryNotifiesOnce() {
        StudentCohortChangedEvent event = cohortEvent(inst, student, "Twice");
        publishCommitted(event);
        publishCommitted(event);
        publishCommitted(cohortEvent(inst, student, "Control"));
        await(() -> notificationsOf(student).stream().anyMatch(n -> n.getTitle().contains("Control"))
                && notificationsOf(student).stream().anyMatch(n -> n.getTitle().contains("Twice")));
        try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        assertThat(notificationsOf(student).stream().filter(n -> n.getTitle().contains("Twice"))).hasSize(1);
    }

    @Test
    void suspendedOrRemovedMembersArentNotifiedButOtherInstitutionsStillWork() {
        Membership m = membershipRepository.findByUserAndInstitution(student, inst).orElseThrow();
        m.setActive(false);
        membershipRepository.save(m);

        Institution b = institution("Second Home");
        member(student, b, Role.STUDENT);

        publishCommitted(cohortEvent(inst, student, "While suspended in A"));
        publishCommitted(cohortEvent(b, student, "From B"));
        await(() -> notificationsOf(student).stream().anyMatch(n -> n.getTitle().contains("From B")));
        assertThat(notificationsOf(student)).noneMatch(n -> n.getTitle().contains("While suspended"));
    }

    @Test
    void notificationsStayInTheirInstitution() {
        Institution b = institution("Other Campus");
        member(student, b, Role.STUDENT);
        publishCommitted(cohortEvent(inst, student, "In A"));
        await(() -> !notificationsOf(student).isEmpty());
        UUID inA = notificationsOf(student).get(0).getPublicId();

        String tokenB = tokenIn(student, b);
        assertThat(get("/api/notifications", tokenB).body().get("items").size()).isZero();
        assertThat(get("/api/notifications/unread-count", tokenB).body().get("count").asInt()).isZero();
        // Its own notification, but from another institution: not found here.
        assertThat(call("PATCH", "/api/notifications/" + inA + "/read", null, tokenB).status()).isEqualTo(404);
        assertThat(call("POST", "/api/notifications/read-all", null, tokenB).body().get("updated").asInt()).isZero();

        String tokenA = tokenIn(student, inst);
        assertThat(get("/api/notifications/unread-count", tokenA).body().get("count").asInt()).isEqualTo(1);
    }

    @Test
    void someoneElsesNotificationIsNotFound() {
        publishCommitted(cohortEvent(inst, student, "Mine"));
        await(() -> !notificationsOf(student).isEmpty());
        UUID theirs = notificationsOf(student).get(0).getPublicId();

        User other = user("other");
        member(other, inst, Role.STUDENT);
        String otherToken = login(other);
        assertThat(call("PATCH", "/api/notifications/" + theirs + "/read", null, otherToken).status()).isEqualTo(404);
        assertThat(call("PATCH", "/api/notifications/" + UUID.randomUUID() + "/read", null, otherToken).status()).isEqualTo(404);
        assertThat(notificationsOf(student).get(0).getReadAt()).isNull();
    }

    @Test
    void inviteAcceptedTellsTheInviterAndLinksToThePerson() {
        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        User joiner = user("joiner");
        member(joiner, inst, Role.FACILITATOR);
        publishCommitted(new InviteAcceptedEvent(inst.getId(), joiner.getId(), owner.getId(),
                joiner.getPublicId(), joiner.getFullName(), "FACILITATOR"));
        await(() -> !notificationsOf(owner).isEmpty());
        Notification n = notificationsOf(owner).get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.INVITE_ACCEPTED);
        assertThat(n.getBody()).contains("a facilitator");
        assertThat(n.getLinkRoute()).isEqualTo("/dashboard/super-admin/users/" + joiner.getPublicId());
        assertThat(n.getLinkPublicId()).isEqualTo(joiner.getPublicId());
    }

    @Test
    void readNotificationsArePurgedAfter180DaysButUnreadOnesStay() {
        Notification oldRead = saved("old read", LocalDateTime.now().minusDays(200));
        Notification oldUnread = saved("old unread", null);
        Notification recentRead = saved("recent read", LocalDateTime.now().minusDays(10));

        cleanupJob.purgeReadNotifications();

        assertThat(notificationRepository.findById(oldRead.getId())).isEmpty();
        assertThat(notificationRepository.findById(oldUnread.getId())).isPresent();
        assertThat(notificationRepository.findById(recentRead.getId())).isPresent();
    }

    private Notification saved(String title, LocalDateTime readAt) {
        Notification n = new Notification();
        n.setRecipient(student);
        n.setInstitution(inst);
        n.setType(NotificationType.COHORT_CHANGED);
        n.setTitle(title);
        n.setBody(title);
        n.setReadAt(readAt);
        n.setDedupeKey(UUID.randomUUID().toString());
        n.setCreatedAt(LocalDateTime.now().minusDays(250));
        return notificationRepository.save(n);
    }
}
