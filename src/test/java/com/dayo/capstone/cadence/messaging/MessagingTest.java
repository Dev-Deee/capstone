package com.dayo.capstone.cadence.messaging;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-app messaging: who may message whom (server-enforced, tenant-scoped),
 * unread counts and mark-read, cohort announcements reaching only enrolled
 * students, and "new message" notifications collapsing per conversation.
 */
class MessagingTest extends IntegrationTestBase {

    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired FacilitatorCohortRepository facilitatorCohortRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionTemplate tx;

    Institution inst, otherInst;
    Cohort cohortA, cohortB;
    Subject subject;
    User owner, admin, facilitator, otherFacilitator, studentA, studentA2, studentB, outsider;

    @BeforeEach
    void setUp() {
        inst = institution("Message College");
        otherInst = institution("Elsewhere College");
        cohortA = cohort(inst);
        cohortB = cohort(inst);
        subject = subject(inst);
        owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        admin = user("admin");
        member(admin, inst, Role.ADMIN);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        teach(facilitator, cohortA);
        otherFacilitator = user("fac2");
        member(otherFacilitator, inst, Role.FACILITATOR);
        teach(otherFacilitator, cohortB);
        studentA = student("stuA", cohortA);
        studentA2 = student("stuA2", cohortA);
        studentB = student("stuB", cohortB);
        outsider = user("outsider");
        member(outsider, otherInst, Role.SUPER_ADMIN);
    }

    // ── Who may message whom ──────────────────────────────────────────────

    @Test
    void studentCannotMessageAnotherStudent() {
        Resp r = start(tokenIn(studentA, inst), studentA2);
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void studentCannotMessageFacilitatorWhoDoesNotTeachThem() {
        Resp r = start(tokenIn(studentA, inst), otherFacilitator);
        assertThat(r.status()).isEqualTo(403);
        // ...and that facilitator can't start one with the student either.
        assertThat(start(tokenIn(otherFacilitator, inst), studentA).status()).isEqualTo(403);
    }

    @Test
    void studentMayMessageTheirFacilitatorAndAdmins() {
        String token = tokenIn(studentA, inst);
        assertThat(start(token, facilitator).status()).isEqualTo(200);
        assertThat(start(token, admin).status()).isEqualTo(200);
        assertThat(start(token, owner).status()).isEqualTo(200);
    }

    @Test
    void staffMayMessageStaff() {
        assertThat(start(tokenIn(otherFacilitator, inst), facilitator).status()).isEqualTo(200);
        assertThat(start(tokenIn(admin, inst), otherFacilitator).status()).isEqualTo(200);
    }

    @Test
    void crossInstitutionIsBlocked() {
        // Someone with no membership here is simply not found.
        assertThat(start(tokenIn(owner, inst), outsider).status()).isEqualTo(404);

        // A conversation can't be reached from another institution, even by a participant.
        member(facilitator, otherInst, Role.FACILITATOR);
        String conv = start(tokenIn(studentA, inst), facilitator).text("publicId");
        String facElsewhere = tokenIn(facilitator, otherInst);
        assertThat(get("/api/conversations/" + conv + "/messages", facElsewhere).status()).isEqualTo(404);
        assertThat(send(facElsewhere, conv, "hello?").status()).isEqualTo(404);

        // And a non-participant in the same institution can't read it.
        assertThat(get("/api/conversations/" + conv + "/messages", tokenIn(admin, inst)).status()).isEqualTo(404);
    }

    @Test
    void reusesTheSameConversationForAPair() {
        String a = start(tokenIn(studentA, inst), facilitator).text("publicId");
        String b = start(tokenIn(facilitator, inst), studentA).text("publicId");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void contactsOnlyListPeopleIMayMessage() {
        Resp r = get("/api/conversations/contacts", tokenIn(studentA, inst));
        assertThat(r.status()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        r.body().forEach(p -> ids.add(p.get("publicId").asText()));
        assertThat(ids).contains(facilitator.getPublicId().toString(), admin.getPublicId().toString(),
                owner.getPublicId().toString());
        assertThat(ids).doesNotContain(studentA2.getPublicId().toString(), otherFacilitator.getPublicId().toString(),
                studentB.getPublicId().toString(), outsider.getPublicId().toString());

        Resp byName = get("/api/conversations/contacts?q=fac", tokenIn(studentA, inst));
        assertThat(byName.body()).hasSize(1);
    }

    // ── Unread and read ───────────────────────────────────────────────────

    @Test
    void unreadCountsAndMarkRead() {
        String facToken = tokenIn(facilitator, inst);
        String stuToken = tokenIn(studentA, inst);
        String conv = start(facToken, studentA).text("publicId");
        assertThat(send(facToken, conv, "First").status()).isEqualTo(201);
        assertThat(send(facToken, conv, "Second").status()).isEqualTo(201);

        assertThat(unread(stuToken)).isEqualTo(2);
        assertThat(unread(facToken)).isZero(); // my own messages are never unread to me
        JsonNode row = get("/api/conversations", stuToken).body().get(0);
        assertThat(row.get("unreadCount").asLong()).isEqualTo(2);
        assertThat(row.get("lastMessagePreview").asText()).isEqualTo("Second");
        assertThat(row.get("lastMessageFromMe").asBoolean()).isFalse();

        Resp page = get("/api/conversations/" + conv + "/messages", stuToken);
        assertThat(page.body().get("items")).hasSize(2);
        assertThat(page.body().get("items").get(1).get("body").asText()).isEqualTo("Second"); // newest last
        String firstId = page.body().get("items").get(0).get("publicId").asText();
        assertThat(get("/api/conversations/" + conv + "/messages?after=" + firstId, stuToken).body().get("items")).hasSize(1);

        assertThat(call("POST", "/api/conversations/" + conv + "/read", null, stuToken).status()).isEqualTo(200);
        assertThat(unread(stuToken)).isZero();

        assertThat(send(stuToken, conv, "Reply").status()).isEqualTo(201);
        assertThat(send(facToken, conv, "Third").status()).isEqualTo(201);
        assertThat(unread(stuToken)).isEqualTo(1);
        assertThat(unread(facToken)).isEqualTo(1);
    }

    @Test
    void rejectsEmptyAndOverlongMessages() {
        String token = tokenIn(facilitator, inst);
        String conv = start(token, studentA).text("publicId");
        assertThat(send(token, conv, "   ").status()).isEqualTo(400);
        assertThat(send(token, conv, "x".repeat(4001)).status()).isEqualTo(400);
        assertThat(send(token, conv, "x".repeat(4000)).status()).isEqualTo(201);
    }

    // ── Notifications ─────────────────────────────────────────────────────

    @Test
    void newMessageNotificationsCollapsePerConversation() {
        String facToken = tokenIn(facilitator, inst);
        String conv = start(facToken, studentA).text("publicId");
        send(facToken, conv, "One");
        await(() -> newMessageNotifications(studentA).size() == 1);
        send(facToken, conv, "Two");
        send(facToken, conv, "Three");
        pause();
        assertThat(newMessageNotifications(studentA)).hasSize(1);
        assertThat(newMessageNotifications(studentA).get(0).getReadAt()).isNull();
        assertThat(newMessageNotifications(facilitator)).isEmpty(); // never the sender

        // Reading the conversation clears it; the next message notifies again.
        call("POST", "/api/conversations/" + conv + "/read", null, tokenIn(studentA, inst));
        assertThat(newMessageNotifications(studentA).get(0).getReadAt()).isNotNull();
        send(facToken, conv, "Four");
        await(() -> newMessageNotifications(studentA).size() == 2);
    }

    // ── Announcements ─────────────────────────────────────────────────────

    @Test
    void announcementsReachOnlyEnrolledStudents() {
        String facToken = tokenIn(facilitator, inst);
        Resp posted = announce(facToken, "Exam moved", cohortA);
        assertThat(posted.status()).isEqualTo(201);
        String id = posted.text("publicId");

        assertThat(feedIds(tokenIn(studentA, inst))).contains(id);
        assertThat(feedIds(tokenIn(studentA2, inst))).contains(id);
        assertThat(feedIds(tokenIn(studentB, inst))).doesNotContain(id);

        await(() -> typesOf(studentA).contains(NotificationType.ANNOUNCEMENT_POSTED));
        pause();
        assertThat(typesOf(studentB)).doesNotContain(NotificationType.ANNOUNCEMENT_POSTED);
    }

    @Test
    void whoMayPostAnnouncements() {
        // A facilitator only to cohorts they teach.
        assertThat(announce(tokenIn(facilitator, inst), "Nope", cohortB).status()).isEqualTo(403);
        // Students can't post at all.
        assertThat(announce(tokenIn(studentA, inst), "Nope", cohortA).status()).isEqualTo(403);
        // An admin without a grant covering the cohort can't; with one, can.
        String adminToken = tokenIn(admin, inst);
        assertThat(announce(adminToken, "Nope", cohortA).status()).isEqualTo(403);
        grant(admin, inst, cohortA, com.dayo.capstone.cadence.common.enums.Permission.MANAGE_COHORTS);
        assertThat(announce(adminToken, "Yes", cohortA).status()).isEqualTo(201);
        assertThat(announce(adminToken, "Nope", cohortB).status()).isEqualTo(403);
        // A super admin can post anywhere here.
        assertThat(announce(tokenIn(owner, inst), "All", cohortA, cohortB).status()).isEqualTo(201);
        // A cohort from another institution is not found.
        Cohort elsewhere = cohort(otherInst);
        assertThat(announce(tokenIn(owner, inst), "Far", elsewhere).status()).isEqualTo(404);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void teach(User f, Cohort c) {
        FacilitatorCohort fc = new FacilitatorCohort();
        fc.setFacilitator(f);
        fc.setCohort(c);
        fc.setSubject(subject);
        fc.setAssignedAt(LocalDateTime.now());
        facilitatorCohortRepository.save(fc);
    }

    private User student(String label, Cohort c) {
        User s = user(label);
        member(s, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(s);
        e.setCohort(c);
        e.setEnrolledAt(LocalDateTime.now().minusDays(1));
        enrollmentRepository.save(e);
        return s;
    }

    private Resp start(String token, User other) {
        return call("POST", "/api/conversations", Map.of("userPublicId", other.getPublicId().toString()), token);
    }

    private Resp send(String token, String conversation, String body) {
        return call("POST", "/api/conversations/" + conversation + "/messages", Map.of("body", body), token);
    }

    private long unread(String token) {
        return get("/api/conversations/unread-count", token).body().get("count").asLong();
    }

    private Resp announce(String token, String title, Cohort... cohorts) {
        return call("POST", "/api/announcements", Map.of(
                "title", title, "body", "Details for " + title,
                "cohortPublicIds", java.util.Arrays.stream(cohorts).map(c -> c.getPublicId().toString()).toList()), token);
    }

    private List<String> feedIds(String token) {
        Resp r = get("/api/announcements/mine", token);
        assertThat(r.status()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        r.body().get("items").forEach(a -> ids.add(a.get("publicId").asText()));
        return ids;
    }

    private List<Notification> newMessageNotifications(User u) {
        return tx.execute(s -> notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(u.getId()) && n.getType() == NotificationType.NEW_MESSAGE)
                .toList());
    }

    private List<NotificationType> typesOf(User u) {
        return tx.execute(s -> notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(u.getId()))
                .map(Notification::getType)
                .toList());
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("Timed out waiting for the notification");
    }

    // Lets async listeners finish before asserting something did NOT happen.
    private static void pause() {
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
