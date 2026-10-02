package com.dayo.capstone.cadence.analytics.service;

import com.dayo.capstone.cadence.analytics.dto.InstitutionAnalyticsResponse;
import com.dayo.capstone.cadence.analytics.dto.InstitutionAnalyticsResponse.Split;
import com.dayo.capstone.cadence.analytics.dto.InstitutionAnalyticsResponse.Today;
import com.dayo.capstone.cadence.analytics.dto.InstitutionAnalyticsResponse.TrendPoint;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.attendance.repository.SessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.institution.model.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * Institution analytics (VIEW_INSTITUTION_ANALYTICS), computed from attendance
 * records over the visible cohorts. "Expected" counts each actively enrolled
 * student once per session held on or after their enrolment date — the same
 * late-joiner rule as the cohort overview. A check-in awaiting biometric
 * confirmation is not attendance.
 *
 * Computed on request, one query per cohort and session: fine at pilot scale;
 * a larger institution would want this pre-aggregated.
 */
@Service
@RequiredArgsConstructor
public class InstitutionAnalyticsService {

    private final CohortService cohortService;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final ResumptionSessionRepository resumptionSessionRepository;
    private final ResumptionAttendanceRecordRepository resumptionRecordRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** Running totals for one day of one series. */
    private static final class Tally {
        int expected, attended;
        void add(int e, int a) { expected += e; attended += a; }
        Integer rate() { return expected == 0 ? null : (int) Math.round(attended * 100.0 / expected); }
    }

    @Transactional(readOnly = true)
    public InstitutionAnalyticsResponse compute(Institution institution, Predicate<Cohort> visible, int days) {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(days - 1L);

        Map<LocalDate, Tally> resumptionByDay = new TreeMap<>();
        Map<LocalDate, Tally> classByDay = new TreeMap<>();
        Tally resumptionTotal = new Tally(), classTotal = new Tally();
        int present = 0, late = 0, pending = 0, expectedResumption = 0;
        int todayExpected = 0, todayPresent = 0, todayLate = 0, todayPending = 0, openSessions = 0;
        int cohortCount = 0;
        Set<Long> students = new HashSet<>();

        for (Cohort cohort : cohortService.getAllByInstitution(institution)) {
            if (!visible.test(cohort)) continue;
            cohortCount++;
            List<CohortEnrollment> enrollments = enrollmentRepository.findByCohortAndIsActiveTrue(cohort);
            enrollments.forEach(e -> students.add(e.getStudent().getId()));

            for (ResumptionSession rs : resumptionSessionRepository.findByCohortSince(cohort, since)) {
                LocalDate day = rs.getResumptionDate();
                Set<Long> expected = enrolledBy(enrollments, day.atTime(23, 59, 59));
                int p = 0, l = 0, pb = 0;
                for (ResumptionAttendanceRecord r : resumptionRecordRepository.findByResumptionSession(rs)) {
                    if (!expected.contains(r.getStudent().getId())) continue;
                    switch (r.getEffectiveStatus()) {
                        case PRESENT -> p++;
                        case LATE -> l++;
                        case PENDING_BIOMETRIC -> pb++;
                        default -> { }
                    }
                }
                resumptionByDay.computeIfAbsent(day, d -> new Tally()).add(expected.size(), p + l);
                resumptionTotal.add(expected.size(), p + l);
                present += p; late += l; pending += pb; expectedResumption += expected.size();
                if (day.equals(today)) {
                    todayExpected += expected.size(); todayPresent += p; todayLate += l; todayPending += pb;
                    if (rs.isCurrentlyOpen()) openSessions++;
                }
            }

            for (Session s : sessionRepository.findByCohortSince(cohort, since.atStartOfDay())) {
                Set<Long> expected = enrolledBy(enrollments, s.getCreatedAt());
                int attended = (int) attendanceRecordRepository.findBySession(s).stream()
                        .filter(r -> expected.contains(r.getStudent().getId()))
                        .map(AttendanceRecord::getStatus)
                        .filter(st -> st != null && st.countsAsAttended())
                        .count();
                classByDay.computeIfAbsent(s.getCreatedAt().toLocalDate(), d -> new Tally()).add(expected.size(), attended);
                classTotal.add(expected.size(), attended);
            }
        }

        List<TrendPoint> trend = new ArrayList<>();
        for (LocalDate d = since; !d.isAfter(today); d = d.plusDays(1)) {
            Tally r = resumptionByDay.get(d), c = classByDay.get(d);
            trend.add(TrendPoint.builder()
                    .date(d)
                    .resumptionRatePercent(r == null ? null : r.rate())
                    .classRatePercent(c == null ? null : c.rate())
                    .build());
        }

        return InstitutionAnalyticsResponse.builder()
                .days(days)
                .cohortCount(cohortCount)
                .studentCount(students.size())
                .resumptionRatePercent(resumptionTotal.rate())
                .classRatePercent(classTotal.rate())
                .today(Today.builder().expected(todayExpected).present(todayPresent).late(todayLate)
                        .pendingBiometric(todayPending).openSessions(openSessions).build())
                .split(Split.builder().present(present).late(late).pendingBiometric(pending)
                        .absent(Math.max(0, expectedResumption - present - late - pending)).build())
                .trend(trend)
                .build();
    }

    private static Set<Long> enrolledBy(List<CohortEnrollment> enrollments, LocalDateTime moment) {
        Set<Long> ids = new HashSet<>();
        for (CohortEnrollment e : enrollments) {
            if (!e.getEnrolledAt().isAfter(moment)) ids.add(e.getStudent().getId());
        }
        return ids;
    }
}
