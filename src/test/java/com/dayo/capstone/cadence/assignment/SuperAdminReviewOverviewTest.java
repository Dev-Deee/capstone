package com.dayo.capstone.cadence.assignment;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.GradingType;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A SUPER_ADMIN lists every assignment in their institution — whoever set it —
 * with how much work is waiting, and can review any of it. Other institutions'
 * assignments never appear; facilitators, admins and students can't use the list.
 */
class SuperAdminReviewOverviewTest extends IntegrationTestBase {

    Institution inst, other;
    User owner, facilitator, facilitator2, student, student2, admin;
    Assignment mine, theirs, foreign;
    Submission waitingWork;

    @BeforeEach
    void setUp() {
        inst = institution("Overview Academy");
        Cohort cohort = cohort(inst);
        Subject subject = subject(inst);
        owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        facilitator2 = user("fac2");
        member(facilitator2, inst, Role.FACILITATOR);
        student = user("stu");
        member(student, inst, Role.STUDENT);
        student2 = user("stu2");
        member(student2, inst, Role.STUDENT);
        admin = user("admin");
        member(admin, inst, Role.ADMIN);

        mine = assignment(cohort, subject, facilitator);
        theirs = assignment(cohort, subject, facilitator2);
        waitingWork = submission(mine, student, SubmissionStatus.SUBMITTED);
        submission(mine, student2, SubmissionStatus.LATE_SUBMITTED);
        submission(theirs, student, SubmissionStatus.REVIEWED);

        other = institution("Elsewhere Academy");
        User otherFac = user("otherfac");
        member(otherFac, other, Role.FACILITATOR);
        User otherStu = user("otherstu");
        member(otherStu, other, Role.STUDENT);
        foreign = assignment(cohort(other), subject(other), otherFac);
        submission(foreign, otherStu, SubmissionStatus.SUBMITTED);
    }

    private Assignment assignment(Cohort cohort, Subject subject, User fac) {
        Assignment a = new Assignment();
        a.setCohort(cohort);
        a.setSubject(subject);
        a.setFacilitator(fac);
        a.setTitle(unique("essay"));
        a.setDeadline(LocalDateTime.now().plusDays(2));
        a.setStatus(AssignmentStatus.OPEN);
        a.setGradingType(GradingType.MARKS);
        a.setMaxMarks(20);
        return assignmentRepository.save(a);
    }

    private Submission submission(Assignment a, User who, SubmissionStatus status) {
        Submission s = new Submission();
        s.setAssignment(a);
        s.setStudent(who);
        s.setStatus(status);
        s.setSubmissionType(SubmissionType.LINK);
        s.setLinkUrl("https://example.com/" + unique("work"));
        s.setSubmittedAt(LocalDateTime.now().minusHours(1));
        return submissionRepository.save(s);
    }

    private JsonNode row(JsonNode list, Assignment a) {
        for (JsonNode r : list) if (r.get("publicId").asText().equals(a.getPublicId().toString())) return r;
        return null;
    }

    @Test
    void superAdminSeesEveryAssignmentInTheirInstitutionWithWaitingCounts() {
        Resp r = get("/api/assignments/review-overview", login(owner));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().size()).isEqualTo(2);

        JsonNode m = row(r.body(), mine);
        assertThat(m).isNotNull();
        assertThat(m.get("waiting").asLong()).isEqualTo(2);
        assertThat(m.get("facilitatorName").asText()).isEqualTo(facilitator.getFullName());
        assertThat(m.get("cohortName").asText()).isEqualTo(mine.getCohort().getName());
        assertThat(m.get("subjectName").asText()).isEqualTo(mine.getSubject().getName());
        assertThat(m.get("gradesReleasedAt").isNull()).isTrue();

        JsonNode t = row(r.body(), theirs);
        assertThat(t).isNotNull();
        assertThat(t.get("waiting").asLong()).isZero();
        assertThat(row(r.body(), foreign)).isNull();
        // Most waiting first.
        assertThat(r.body().get(0).get("publicId").asText()).isEqualTo(mine.getPublicId().toString());
    }

    @Test
    void superAdminWaitingCountCoversTheWholeInstitution_facilitatorsOnlyTheirOwn() {
        assertThat(get("/api/submissions/waiting-count", login(owner)).body().get("waiting").asLong()).isEqualTo(2);
        assertThat(get("/api/submissions/waiting-count", login(facilitator)).body().get("waiting").asLong()).isEqualTo(2);
        assertThat(get("/api/submissions/waiting-count", login(facilitator2)).body().get("waiting").asLong()).isZero();
    }

    @Test
    void superAdminCanReviewAnotherFacilitatorsWork() {
        String token = login(owner);
        assertThat(get("/api/assignments/" + mine.getPublicId() + "/submissions", token).status()).isEqualTo(200);
        Map<String, Object> body = new HashMap<>();
        body.put("decision", "ACCEPT");
        body.put("score", 15);
        Resp r = call("POST", "/api/submissions/" + waitingWork.getPublicId() + "/review", body, token);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.text("status")).isEqualTo("REVIEWED");
    }

    @Test
    void facilitatorsAdminsAndStudentsAreForbidden() {
        assertThat(get("/api/assignments/review-overview", login(facilitator)).status()).isEqualTo(403);
        assertThat(get("/api/assignments/review-overview", login(admin)).status()).isEqualTo(403);
        assertThat(get("/api/assignments/review-overview", login(student)).status()).isEqualTo(403);
        assertThat(get("/api/assignments/review-overview", null).status()).isIn(401, 403);
    }
}
