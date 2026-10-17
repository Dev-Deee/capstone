package com.dayo.capstone.cadence.analytics.service;

import com.dayo.capstone.cadence.analytics.dto.FacilitatorCohortAnalytics;
import com.dayo.capstone.cadence.analytics.dto.FacilitatorCohortAnalytics.AssignmentStats;
import com.dayo.capstone.cadence.analytics.dto.FacilitatorCohortAnalytics.StudentStats;
import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.ExtensionRequestRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
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
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics for one cohort a facilitator teaches. Campus resumption covers the
 * whole cohort; class attendance and assignments cover only this facilitator's
 * own subjects and work. Same expected-attendance rule as the institution view:
 * a student counts for a session only from their enrolment onwards, and a
 * check-in awaiting biometric isn't attendance.
 */
@Service
@RequiredArgsConstructor
public class FacilitatorAnalyticsService {

    private final InstitutionAnalyticsService institutionAnalytics;
    private final FacilitatorCohortRepository facilitatorCohortRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final ResumptionSessionRepository resumptionSessionRepository;
    private final ResumptionAttendanceRecordRepository resumptionRecordRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final ExtensionRequestRepository extensionRequestRepository;

    @Transactional(readOnly = true)
    public FacilitatorCohortAnalytics compute(User facilitator, Cohort cohort, Institution institution, int days) {
        Set<Long> mySubjects = facilitatorCohortRepository.findByFacilitatorAndIsActiveTrue(facilitator).stream()
                .filter(fc -> fc.getCohort().getId().equals(cohort.getId()))
                .map(FacilitatorCohort::getSubject)
                .map(s -> s.getId())
                .collect(Collectors.toSet());
        if (mySubjects.isEmpty()) {
            throw new AccessDeniedException("You don't teach this cohort");
        }
        LocalDate since = LocalDate.now().minusDays(days - 1L);

        var attendance = institutionAnalytics.compute(institution,
                c -> c.getId().equals(cohort.getId()), days,
                s -> s.getFacilitator().getId().equals(facilitator.getId()) && mySubjects.contains(s.getSubject().getId()));

        List<CohortEnrollment> enrollments = enrollmentRepository.findByCohortAndIsActiveTrue(cohort);

        // Per-student attendance tallies: [expected, attended] for each series.
        Map<Long, int[]> campus = new HashMap<>(), classes = new HashMap<>();
        for (ResumptionSession rs : resumptionSessionRepository.findByCohortSince(cohort, since)) {
            Set<Long> attended = resumptionRecordRepository.findByResumptionSession(rs).stream()
                    .filter(r -> r.getEffectiveStatus().countsAsAttended())
                    .map(ResumptionAttendanceRecord::getStudent).map(User::getId).collect(Collectors.toSet());
            for (CohortEnrollment e : enrollments) {
                if (e.getEnrolledAt().toLocalDate().isAfter(rs.getResumptionDate())) continue;
                int[] t = campus.computeIfAbsent(e.getStudent().getId(), k -> new int[2]);
                t[0]++;
                if (attended.contains(e.getStudent().getId())) t[1]++;
            }
        }
        for (Session s : sessionRepository.findByCohortSince(cohort, since.atStartOfDay())) {
            if (!s.getFacilitator().getId().equals(facilitator.getId()) || !mySubjects.contains(s.getSubject().getId())) continue;
            Set<Long> attended = attendanceRecordRepository.findBySession(s).stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().countsAsAttended())
                    .map(AttendanceRecord::getStudent).map(User::getId).collect(Collectors.toSet());
            for (CohortEnrollment e : enrollments) {
                if (e.getEnrolledAt().isAfter(s.getCreatedAt())) continue;
                int[] t = classes.computeIfAbsent(e.getStudent().getId(), k -> new int[2]);
                t[0]++;
                if (attended.contains(e.getStudent().getId())) t[1]++;
            }
        }

        // This facilitator's published work in the cohort, newest deadline first.
        List<Assignment> work = assignmentRepository.findByCohort(cohort).stream()
                .filter(a -> a.getFacilitator().getId().equals(facilitator.getId()))
                .filter(a -> a.getStatus() != AssignmentStatus.DRAFT)
                .sorted(Comparator.comparing(Assignment::getDeadline).reversed())
                .toList();
        Map<Long, int[]> perStudentWork = new HashMap<>(); // [handedIn, late, missing]
        List<AssignmentStats> assignmentStats = new ArrayList<>();
        for (Assignment a : work) {
            int onTime = 0, late = 0, reviewed = 0, missing = 0, notYet = 0;
            List<Submission> subs = submissionRepository.findByAssignment(a);
            for (Submission sub : subs) {
                int[] w = perStudentWork.computeIfAbsent(sub.getStudent().getId(), k -> new int[3]);
                switch (sub.getStatus()) {
                    case SUBMITTED -> { onTime++; w[0]++; }
                    case LATE_SUBMITTED -> { late++; w[0]++; w[1]++; }
                    case REVIEWED -> { reviewed++; w[0]++; }
                    case MISSING -> { missing++; w[2]++; }
                    case PENDING -> notYet++;
                }
            }
            assignmentStats.add(AssignmentStats.builder()
                    .publicId(a.getPublicId()).title(a.getTitle()).subjectName(a.getSubject().getName())
                    .status(a.getStatus()).deadline(a.getDeadline()).total(subs.size())
                    .onTime(onTime).late(late).reviewed(reviewed).missing(missing).notYet(notYet)
                    .build());
        }

        List<StudentStats> students = enrollments.stream().map(e -> {
            User st = e.getStudent();
            int[] c = campus.getOrDefault(st.getId(), new int[2]);
            int[] k = classes.getOrDefault(st.getId(), new int[2]);
            int[] w = perStudentWork.getOrDefault(st.getId(), new int[3]);
            return StudentStats.builder()
                    .publicId(st.getPublicId()).name(st.getFullName())
                    .resumptionRatePercent(rate(c)).classRatePercent(rate(k))
                    .handedIn(w[0]).late(w[1]).missing(w[2])
                    .build();
        })
        // Most in need of attention first: missed work, then lowest attendance.
        .sorted(Comparator.comparingInt((StudentStats s) -> -s.missing())
                .thenComparingInt(s -> Math.min(orFull(s.resumptionRatePercent()), orFull(s.classRatePercent())))
                .thenComparing(StudentStats::name))
        .toList();

        int pendingExtensions = (int) extensionRequestRepository
                .findBySubmission_Assignment_FacilitatorAndStatus(facilitator, ExtensionRequestStatus.PENDING_APPROVAL).stream()
                .filter(x -> x.getSubmission().getAssignment().getCohort().getId().equals(cohort.getId()))
                .count();

        return FacilitatorCohortAnalytics.builder()
                .cohortPublicId(cohort.getPublicId()).cohortName(cohort.getName()).days(days)
                .attendance(attendance).pendingExtensions(pendingExtensions)
                .assignments(assignmentStats).students(students)
                .build();
    }

    private static Integer rate(int[] t) {
        return t[0] == 0 ? null : (int) Math.round(t[1] * 100.0 / t[0]);
    }

    private static int orFull(Integer v) {
        return v == null ? 100 : v;
    }
}
