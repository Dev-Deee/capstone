package com.dayo.capstone.cadence.assignment;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.GradingType;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewing handed-in work: accept with a grade or send back to redo, grades
 * held until released, more time and attempts at the facilitator's discretion,
 * audited regrades, and only the assignment's own facilitator (or a SUPER_ADMIN).
 */
class ReviewAndGradingTest extends IntegrationTestBase {

    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionTemplate tx;

    Institution inst;
    Cohort cohort;
    Subject subject;
    User facilitator, otherFacilitator, student, student2;
    Assignment assignment;
    Submission work, work2;
    String facToken;

    @BeforeEach
    void setUp() {
        inst = institution("Review Academy");
        cohort = cohort(inst);
        subject = subject(inst);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        otherFacilitator = user("fac2");
        member(otherFacilitator, inst, Role.FACILITATOR);
        student = user("stu");
        member(student, inst, Role.STUDENT);
        student2 = user("stu2");
        member(student2, inst, Role.STUDENT);
        assignment = assignment(GradingType.MARKS, 20);
        work = handedIn(student, false);
        work2 = handedIn(student2, false);
        facToken = login(facilitator);
    }

    private Assignment assignment(GradingType type, Integer maxMarks) {
        Assignment a = new Assignment();
        a.setCohort(cohort);
        a.setSubject(subject);
        a.setFacilitator(facilitator);
        a.setTitle(unique("essay"));
        a.setDeadline(LocalDateTime.now().plusDays(2));
        a.setStatus(AssignmentStatus.OPEN);
        a.setGradingType(type);
        a.setMaxMarks(maxMarks);
        return assignmentRepository.save(a);
    }

    private Submission handedIn(User who, boolean late) {
        Submission s = new Submission();
        s.setAssignment(assignment);
        s.setStudent(who);
        s.setStatus(late ? SubmissionStatus.LATE_SUBMITTED : SubmissionStatus.SUBMITTED);
        s.setSubmissionType(SubmissionType.LINK);
        s.setLinkUrl("https://example.com/" + unique("work"));
        s.setSubmittedAt(LocalDateTime.now().minusHours(1));
        return submissionRepository.save(s);
    }

    private Resp review(Submission s, Map<String, Object> body, String token) {
        return call("POST", "/api/submissions/" + s.getPublicId() + "/review", body, token);
    }

    private Map<String, Object> accept(Object score, String feedback) {
        Map<String, Object> m = new HashMap<>();
        m.put("decision", "ACCEPT");
        m.put("score", score);
        m.put("feedback", feedback);
        return m;
    }

    private Map<String, Object> redo(String feedback) {
        Map<String, Object> m = new HashMap<>();
        m.put("decision", "REDO");
        m.put("feedback", feedback);
        return m;
    }

    private JsonNode mine(User who) {
        Resp r = get("/api/submissions/mine", login(who));
        for (JsonNode n : r.body()) {
            if (n.get("assignmentPublicId").asText().equals(assignment.getPublicId().toString())) return n;
        }
        throw new AssertionError("no submission for " + who.getEmail());
    }

    private Resp resubmitLink(Submission s, String token) {
        String boundary = "----cadence" + System.nanoTime();
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"submissionType\"\r\n\r\nLINK\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"linkUrl\"\r\n\r\nhttps://example.com/redo\r\n"
                + "--" + boundary + "--\r\n";
        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/submissions/" + s.getPublicId() + "/submit"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Resp(res.statusCode(), res.body().isBlank() ? null : new ObjectMapper().readTree(res.body()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("Timed out waiting for the notification");
    }

    private List<NotificationType> notificationTypesOf(User u) {
        return tx.execute(s -> notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(u.getId()))
                .map(Notification::getType)
                .toList());
    }

    @Test
    void theFacilitatorSeesEveryStudentsWorkWithWaitingFirst() {
        User notYet = user("stu3");
        member(notYet, inst, Role.STUDENT);
        Submission pending = new Submission();
        pending.setAssignment(assignment);
        pending.setStudent(notYet);
        submissionRepository.save(pending);

        Resp r = get("/api/assignments/" + assignment.getPublicId() + "/submissions", facToken);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().size()).isEqualTo(3);
        assertThat(r.body().get(0).get("status").asText()).isIn("SUBMITTED", "LATE_SUBMITTED");
        assertThat(r.body().get(2).get("status").asText()).isEqualTo("PENDING");
        assertThat(get("/api/submissions/waiting-count", facToken).body().get("waiting").asLong()).isEqualTo(2);
    }

    @Test
    void onlyTheOwnFacilitatorOrASuperAdminCanReview() {
        String otherToken = login(otherFacilitator);
        assertThat(get("/api/assignments/" + assignment.getPublicId() + "/submissions", otherToken).status()).isEqualTo(403);
        assertThat(review(work, accept(15, "ok"), otherToken).status()).isEqualTo(403);
        assertThat(review(work, accept(15, "ok"), login(student)).status()).isEqualTo(403);

        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        assertThat(review(work, accept(15, "Solid"), login(owner)).status()).isEqualTo(200);
    }

    @Test
    void acceptedGradesStayHiddenUntilReleasedThenEveryoneSeesThemAtOnce() {
        Resp ok = review(work, accept(17, "Clear argument"), facToken);
        assertThat(ok.status()).as(ok.toString()).isEqualTo(200);
        assertThat(ok.text("status")).isEqualTo("REVIEWED");

        // Before release the student sees their work as handed in, with no grade.
        JsonNode before = mine(student);
        assertThat(before.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(before.get("score").isNull()).isTrue();
        assertThat(before.get("feedback").isNull()).isTrue();

        Resp released = call("POST", "/api/assignments/" + assignment.getPublicId() + "/release-grades", null, facToken);
        assertThat(released.status()).as(released.toString()).isEqualTo(200);
        assertThat(released.text("gradesReleasedAt")).isNotNull();

        JsonNode after = mine(student);
        assertThat(after.get("status").asText()).isEqualTo("REVIEWED");
        assertThat(after.get("score").decimalValue()).isEqualByComparingTo(new BigDecimal("17"));
        assertThat(after.get("feedback").asText()).isEqualTo("Clear argument");
        await(() -> notificationTypesOf(student).contains(NotificationType.GRADES_RELEASED));

        // Work accepted after the release is visible straight away.
        assertThat(review(work2, accept(12, "Needs sources"), facToken).status()).isEqualTo(200);
        assertThat(mine(student2).get("score").decimalValue()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(call("POST", "/api/assignments/" + assignment.getPublicId() + "/release-grades", null, facToken).status())
                .isEqualTo(400);
    }

    @Test
    void aGradeMustFitTheAssignmentsGradingType() {
        assertThat(review(work, accept(null, "no mark"), facToken).message()).contains("mark out of 20");
        assertThat(review(work, accept(21, "too high"), facToken).message()).contains("between 0 and 20");

        Assignment passFail = assignment(GradingType.PASS_FAIL, null);
        assignment = passFail;
        Submission pf = handedIn(student, false);
        Map<String, Object> body = new HashMap<>(Map.of("decision", "ACCEPT"));
        assertThat(review(pf, body, facToken).message()).contains("pass or fail");
        body.put("passed", true);
        assertThat(review(pf, body, facToken).status()).isEqualTo(200);

        assignment = assignment(GradingType.FEEDBACK_ONLY, null);
        Submission fb = handedIn(student, false);
        assertThat(review(fb, new HashMap<>(Map.of("decision", "ACCEPT")), facToken).message()).contains("feedback");
        assertThat(review(fb, accept(null, "Well done"), facToken).status()).isEqualTo(200);
    }

    @Test
    void sendingBackNotifiesAtOnceAndTheStudentCanRedoAfterTheDeadline() {
        assertThat(review(work, new HashMap<>(Map.of("decision", "REDO")), facToken).message()).contains("redone");

        Resp sent = review(work, redo("Add your references"), facToken);
        assertThat(sent.status()).as(sent.toString()).isEqualTo(200);
        assertThat(sent.text("status")).isEqualTo("REDO_REQUESTED");
        assertThat(LocalDateTime.parse(sent.text("redoDueAt"))).isAfter(LocalDateTime.now().plusDays(2).plusHours(23));

        // Visible immediately — a send-back is not held for release.
        JsonNode seen = mine(student);
        assertThat(seen.get("status").asText()).isEqualTo("REDO_REQUESTED");
        assertThat(seen.get("feedback").asText()).isEqualTo("Add your references");
        await(() -> notificationTypesOf(student).contains(NotificationType.SUBMISSION_SENT_BACK));

        // The original deadline passes and the assignment closes; the redo still goes in.
        Assignment a = assignmentRepository.findById(assignment.getId()).orElseThrow();
        a.setDeadline(LocalDateTime.now().minusHours(1));
        a.setStatus(AssignmentStatus.CLOSED);
        assignmentRepository.save(a);

        Resp again = resubmitLink(work, login(student));
        assertThat(again.status()).as(again.toString()).isEqualTo(200);
        assertThat(again.text("status")).isEqualTo("SUBMITTED");

        // Back in the facilitator's waiting list, with the first attempt kept.
        Resp rows = get("/api/assignments/" + assignment.getPublicId() + "/submissions", facToken);
        JsonNode row = null;
        for (JsonNode n : rows.body()) if (n.get("publicId").asText().equals(work.getPublicId().toString())) row = n;
        assertThat(row).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(row.get("attempts").size()).isEqualTo(1);
        assertThat(row.get("attempts").get(0).get("decision").asText()).isEqualTo("REDO");
    }

    @Test
    void theFacilitatorCanGiveMoreTimeAndAnotherAttempt() {
        Submission s = submissionRepository.findById(work.getId()).orElseThrow();
        s.setResubmissionCount(assignment.getMaxResubmissions()); // every attempt used
        submissionRepository.save(s);

        assertThat(review(work, redo("Fix the intro"), facToken).message()).contains("No attempts left");

        Resp more = call("POST", "/api/submissions/" + work.getPublicId() + "/redo-extension",
                Map.of("grantExtraAttempt", true), facToken);
        assertThat(more.status()).as(more.toString()).isEqualTo(200);
        assertThat(more.body().get("attemptsAllowed").asInt()).isEqualTo(assignment.getMaxResubmissions() + 1);
        assertThat(review(work, redo("Fix the intro"), facToken).status()).isEqualTo(200);

        String later = LocalDateTime.now().plusDays(10).withNano(0).toString();
        Resp moved = call("POST", "/api/submissions/" + work.getPublicId() + "/redo-extension",
                Map.of("redoDueAt", later), facToken);
        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.text("redoDueAt")).startsWith(later.substring(0, 16));

        // The redo window closes: the student is told to ask for more time.
        s = submissionRepository.findById(work.getId()).orElseThrow();
        s.setRedoDueAt(LocalDateTime.now().minusMinutes(1));
        submissionRepository.save(s);
        assertThat(resubmitLink(work, login(student)).message()).contains("Ask your facilitator for more time");
    }

    @Test
    void changingAnAcceptedGradeNeedsAReasonAndIsAudited() {
        review(work, accept(14, "Good"), facToken);
        assertThat(call("PATCH", "/api/submissions/" + work.getPublicId() + "/grade", Map.of("score", 16), facToken).status())
                .isEqualTo(400);
        Resp changed = call("PATCH", "/api/submissions/" + work.getPublicId() + "/grade",
                Map.of("score", 16, "reason", "Missed the appendix on first read"), facToken);
        assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
        assertThat(changed.body().get("score").decimalValue()).isEqualByComparingTo(new BigDecimal("16"));
        assertThat(changed.body().get("attempts").get(0).get("score").decimalValue()).isEqualByComparingTo(new BigDecimal("14"));
        assertThat(auditLogRepository.findAll()).anyMatch(l -> l.getAction() == AuditAction.UPDATE
                && "SubmissionGrade".equals(l.getEntityType())
                && l.getAfterState().contains("Missed the appendix"));
    }

    @Test
    void reviewedWorkCannotBeOverwrittenOrReviewedTwice() {
        review(work, accept(18, "Great"), facToken);
        assertThat(resubmitLink(work, login(student)).message()).contains("already been reviewed");
        assertThat(review(work, accept(10, "again"), facToken).message()).contains("waiting for review");
    }

    @Test
    void newAssignmentsDefaultToMarksOutOf100() {
        com.dayo.capstone.cadence.cohort.model.FacilitatorCohort fc = new com.dayo.capstone.cadence.cohort.model.FacilitatorCohort();
        fc.setFacilitator(facilitator);
        fc.setCohort(cohort);
        fc.setSubject(subject);
        fc.setAssignedAt(LocalDateTime.now());
        facilitatorCohortRepository.save(fc);
        Resp created = call("POST", "/api/assignments", Map.of(
                "cohortPublicId", cohort.getPublicId().toString(),
                "subjectPublicId", subject.getPublicId().toString(),
                "title", "Defaults", "deadline", LocalDateTime.now().plusDays(3).withNano(0).toString()), facToken);
        assertThat(created.status()).as(created.toString()).isEqualTo(201);
        assertThat(created.text("gradingType")).isEqualTo("MARKS");
        assertThat(created.body().get("maxMarks").asInt()).isEqualTo(100);
    }

    @Autowired com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository facilitatorCohortRepository;
}
