package com.dayo.capstone.cadence.admin;

import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analytics dashboard's numbers on a known fixture, and its permission
 * rule (the same as the admin attendance reports).
 *
 * Cohort 1, two students: today's resumption — one PRESENT, one awaiting
 * biometric; yesterday's — one LATE, one with no record; today's class — one
 * PRESENT. Cohort 2, one student, no records.
 */
class InstitutionAnalyticsTest extends IntegrationTestBase {

    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired ResumptionAttendanceRecordRepository resumptionRecordRepository;
    @Autowired AttendanceRecordRepository attendanceRecordRepository;

    Institution inst;
    Cohort cohort1, cohort2;
    User owner, a;

    @BeforeEach
    void setUp() {
        inst = institution("Analytics Dashboard U");
        owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        cohort1 = cohort(inst);
        cohort2 = cohort(inst);
        a = student(cohort1);
        User b = student(cohort1);
        student(cohort2);
        resumptionSession(cohort2);

        ResumptionSession today = resumptionSession(cohort1);
        record(today, a, AttendanceStatus.PRESENT);
        record(today, b, AttendanceStatus.PENDING_BIOMETRIC);
        ResumptionSession yesterday = resumptionSession(cohort1, LocalDate.now().minusDays(1));
        yesterday.setExpiresAt(LocalDateTime.now().minusHours(20));
        resumptionSessionRepository.save(yesterday);
        record(yesterday, a, AttendanceStatus.LATE);

        User fac = user("fac");
        member(fac, inst, Role.FACILITATOR);
        Session cls = session(cohort1, subject(inst), fac);
        AttendanceRecord r = new AttendanceRecord();
        r.setSession(cls);
        r.setStudent(a);
        r.setStatus(AttendanceStatus.PRESENT);
        r.setVerificationMethod(VerificationMethod.CODE);
        r.setSubmittedAt(LocalDateTime.now());
        attendanceRecordRepository.save(r);
    }

    private User student(Cohort cohort) {
        User s = user("stu");
        member(s, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(s);
        e.setCohort(cohort);
        e.setEnrolledAt(LocalDateTime.now().minusDays(10));
        enrollmentRepository.save(e);
        return s;
    }

    private void record(ResumptionSession rs, User student, AttendanceStatus status) {
        ResumptionAttendanceRecord r = new ResumptionAttendanceRecord();
        r.setResumptionSession(rs);
        r.setStudent(student);
        r.setCheckedInAt(LocalDateTime.now());
        r.setStatus(status);
        r.setVerificationMethod(VerificationMethod.CODE);
        resumptionRecordRepository.save(r);
    }

    @Test
    void numbersMatchTheRecords() {
        Resp r = get("/api/admin/analytics?days=7", login(owner));
        assertThat(r.status()).isEqualTo(200);
        JsonNode b = r.body();
        assertThat(b.get("cohortCount").asInt()).isEqualTo(2);
        assertThat(b.get("studentCount").asInt()).isEqualTo(3);
        // Resumption: expected 2 (today c1) + 2 (yesterday c1) + 1 (today c2) = 5; attended PRESENT + LATE = 2.
        assertThat(b.get("resumptionRatePercent").asInt()).isEqualTo(40);
        assertThat(b.get("classRatePercent").asInt()).isEqualTo(50);
        JsonNode today = b.get("today");
        assertThat(today.get("expected").asInt()).isEqualTo(3);
        assertThat(today.get("present").asInt()).isEqualTo(1);
        assertThat(today.get("pendingBiometric").asInt()).isEqualTo(1);
        assertThat(today.get("openSessions").asInt()).isEqualTo(2);
        JsonNode split = b.get("split");
        assertThat(split.get("present").asInt()).isEqualTo(1);
        assertThat(split.get("late").asInt()).isEqualTo(1);
        assertThat(split.get("pendingBiometric").asInt()).isEqualTo(1);
        assertThat(split.get("absent").asInt()).isEqualTo(2);
        JsonNode trend = b.get("trend");
        assertThat(trend.size()).isEqualTo(7);
        JsonNode last = trend.get(6), prev = trend.get(5), first = trend.get(0);
        assertThat(last.get("resumptionRatePercent").asInt()).isEqualTo(33); // 1 of 3 today
        assertThat(prev.get("resumptionRatePercent").asInt()).isEqualTo(50); // 1 of 2 yesterday
        assertThat(first.get("resumptionRatePercent").isNull()).isTrue();   // nothing held
    }

    @Test
    void cohortScopedGrantCountsOnlyItsCohort() {
        User admin = user("analyst");
        member(admin, inst, Role.ADMIN);
        grant(admin, inst, cohort2, Permission.VIEW_INSTITUTION_ANALYTICS);
        JsonNode b = get("/api/admin/analytics?days=7", login(admin)).body();
        assertThat(b.get("cohortCount").asInt()).isEqualTo(1);
        assertThat(b.get("studentCount").asInt()).isEqualTo(1);
        assertThat(b.get("today").get("present").asInt()).isZero();
    }

    @Test
    void adminWithoutTheGrantIsForbidden() {
        User admin = user("noperm");
        member(admin, inst, Role.ADMIN);
        grant(admin, inst, Permission.MANAGE_COHORTS);
        assertThat(get("/api/admin/analytics", login(admin)).status()).isEqualTo(403);
        assertThat(get("/api/admin/analytics?days=3", login(owner)).status()).isEqualTo(400);
    }

    @Test
    void oneStudentsHistoryFollowsTheSameRule() {
        Resp mine = get("/api/admin/analytics/students/" + a.getPublicId(), login(owner));
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.text("cohortName")).isEqualTo(cohort1.getName());
        assertThat(mine.body().get("resumption").findValuesAsText("status")).contains("PRESENT", "LATE");
        assertThat(mine.body().get("classes").findValuesAsText("status")).contains("PRESENT");

        User analyst = user("scoped");
        member(analyst, inst, Role.ADMIN);
        grant(analyst, inst, cohort2, Permission.VIEW_INSTITUTION_ANALYTICS);
        assertThat(get("/api/admin/analytics/students/" + a.getPublicId(), login(analyst)).status()).isEqualTo(403);
    }

    @Test
    void studentsElsewhereAreInvisible() {
        Institution other = institution("Elsewhere");
        Cohort otherCohort = cohort(other);
        User outsider = user("outsider");
        member(outsider, other, Role.STUDENT);
        assertThat(get("/api/admin/analytics/students/" + outsider.getPublicId(), login(owner)).status()).isEqualTo(404);

        // A member here whose active cohort belongs to another institution: no records leak.
        User both = user("both");
        member(both, inst, Role.STUDENT);
        member(both, other, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(both);
        e.setCohort(otherCohort);
        e.setEnrolledAt(LocalDateTime.now().minusDays(3));
        enrollmentRepository.save(e);
        Resp r = get("/api/admin/analytics/students/" + both.getPublicId(), login(owner));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.text("cohortName")).isNull();
        assertThat(r.body().get("resumption").size()).isZero();
    }
}
