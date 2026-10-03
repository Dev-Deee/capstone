package com.dayo.capstone.cadence.admin;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A facilitator's per-cohort analytics. Fixture, one cohort, two students:
 * - campus resumption today: s1 present, s2 not
 * - my class session: s1 present, s2 absent
 * - another facilitator's session: s2 present (must not count)
 * - my published assignment: s1 on time, s2 missing; my draft and the other
 *   facilitator's assignment must not count.
 */
class FacilitatorAnalyticsTest extends IntegrationTestBase {

    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired FacilitatorCohortRepository facilitatorCohortRepository;
    @Autowired ResumptionAttendanceRecordRepository resumptionRecordRepository;
    @Autowired AttendanceRecordRepository attendanceRecordRepository;

    Institution inst;
    Cohort cohort;
    User fac, otherFac, s1, s2;

    @BeforeEach
    void setUp() {
        inst = institution("Facilitator Analytics U");
        cohort = cohort(inst);
        Subject mine = subject(inst), theirs = subject(inst);
        fac = facilitator(mine);
        otherFac = facilitator(theirs);
        s1 = student();
        s2 = student();

        var rs = resumptionSession(cohort);
        ResumptionAttendanceRecord rr = new ResumptionAttendanceRecord();
        rr.setResumptionSession(rs);
        rr.setStudent(s1);
        rr.setCheckedInAt(LocalDateTime.now());
        rr.setStatus(AttendanceStatus.PRESENT);
        rr.setVerificationMethod(VerificationMethod.CODE);
        resumptionRecordRepository.save(rr);

        mark(session(cohort, mine, fac), s1);
        mark(session(cohort, theirs, otherFac), s2);

        Assignment published = assignment(mine, fac, AssignmentStatus.OPEN, "Mine, published");
        submission(published, s1, SubmissionStatus.SUBMITTED);
        submission(published, s2, SubmissionStatus.MISSING);
        assignment(mine, fac, AssignmentStatus.DRAFT, "Mine, draft");
        Assignment other = assignment(theirs, otherFac, AssignmentStatus.OPEN, "Theirs");
        submission(other, s1, SubmissionStatus.MISSING);
    }

    private User facilitator(Subject subject) {
        User f = user("fac");
        member(f, inst, Role.FACILITATOR);
        FacilitatorCohort fc = new FacilitatorCohort();
        fc.setFacilitator(f);
        fc.setCohort(cohort);
        fc.setSubject(subject);
        fc.setAssignedAt(LocalDateTime.now());
        facilitatorCohortRepository.save(fc);
        return f;
    }

    private User student() {
        User s = user("stu");
        member(s, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(s);
        e.setCohort(cohort);
        e.setEnrolledAt(LocalDateTime.now().minusDays(5));
        enrollmentRepository.save(e);
        return s;
    }

    private void mark(Session session, User student) {
        AttendanceRecord r = new AttendanceRecord();
        r.setSession(session);
        r.setStudent(student);
        r.setStatus(AttendanceStatus.PRESENT);
        r.setVerificationMethod(VerificationMethod.CODE);
        r.setSubmittedAt(LocalDateTime.now());
        attendanceRecordRepository.save(r);
    }

    private Assignment assignment(Subject subject, User owner, AssignmentStatus status, String title) {
        Assignment a = new Assignment();
        a.setCohort(cohort);
        a.setSubject(subject);
        a.setFacilitator(owner);
        a.setTitle(title);
        a.setStatus(status);
        a.setDeadline(LocalDateTime.now().plusDays(2));
        return assignmentRepository.save(a);
    }

    private void submission(Assignment a, User student, SubmissionStatus status) {
        Submission sub = new Submission();
        sub.setAssignment(a);
        sub.setStudent(student);
        sub.setStatus(status);
        submissionRepository.save(sub);
    }

    private Resp analytics(User who) {
        return get("/api/facilitator/analytics?cohortPublicId=" + cohort.getPublicId() + "&days=7", login(who));
    }

    @Test
    void countsOnlyThisFacilitatorsClassesAndPublishedWork() {
        Resp r = analytics(fac);
        assertThat(r.status()).isEqualTo(200);
        JsonNode b = r.body();
        assertThat(b.get("attendance").get("resumptionRatePercent").asInt()).isEqualTo(50);
        assertThat(b.get("attendance").get("classRatePercent").asInt()).isEqualTo(50); // their session excluded

        JsonNode work = b.get("assignments");
        assertThat(work.size()).isEqualTo(1);
        assertThat(work.get(0).get("title").asText()).isEqualTo("Mine, published");
        assertThat(work.get(0).get("onTime").asInt()).isEqualTo(1);
        assertThat(work.get(0).get("missing").asInt()).isEqualTo(1);

        JsonNode students = b.get("students");
        assertThat(students.get(0).get("publicId").asText()).isEqualTo(s2.getPublicId().toString()); // missed work first
        assertThat(students.get(0).get("missing").asInt()).isEqualTo(1);
        assertThat(students.get(0).get("classRatePercent").asInt()).isZero();
        assertThat(students.get(1).get("resumptionRatePercent").asInt()).isEqualTo(100);
        assertThat(students.get(1).get("missing").asInt()).isZero(); // the other facilitator's missing work doesn't count
    }

    @Test
    void facilitatorsWhoDontTeachTheCohortAreRefused() {
        User stranger = user("stranger");
        member(stranger, inst, Role.FACILITATOR);
        assertThat(analytics(stranger).status()).isEqualTo(403);

        User admin = user("admin");
        member(admin, inst, Role.ADMIN);
        assertThat(analytics(admin).status()).isEqualTo(403);
    }
}
