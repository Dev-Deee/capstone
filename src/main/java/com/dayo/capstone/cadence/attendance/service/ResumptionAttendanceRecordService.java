package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.MarkResumptionAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceHistoryEntry;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceSummaryResponse;
import com.dayo.capstone.cadence.attendance.dto.response.CohortAttendanceOverviewEntry;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.dto.response.StudentAttendanceSummaryEntry;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumptionAttendanceRecordService {

    private final ResumptionAttendanceRecordRepository recordRepository;
    private final ResumptionSessionService resumptionSessionService;
    private final ResumptionSessionRepository resumptionSessionRepository;
    private final TotpService totpService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final CohortService cohortService;

    @Transactional
    public ResumptionAttendanceRecordResponse markAttendance(MarkResumptionAttendanceRequest request, User student) {
        // Resolve the student's active cohort — same gate as class attendance
        // (a valid code from another cohort must not let a student through).
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not enrolled in any active cohort"));

        Cohort cohort = enrollment.getCohort();

        // Find all currently-open resumption sessions for this cohort, then TOTP-validate.
        List<ResumptionSession> candidates = resumptionSessionService.findOpenResumptionSessionsByCohort(cohort);
        ResumptionSession session = candidates.stream()
                .filter(s -> totpService.isValid(s.getCodeSecret(), request.getResumptionCode(), TotpService.PERIOD_SECONDS))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resumption code is invalid or has expired"));

        recordRepository.findByResumptionSessionAndStudent(session, student)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Resumption attendance already marked for today");
                });

        ResumptionAttendanceRecord record = new ResumptionAttendanceRecord();
        record.setResumptionSession(session);
        record.setStudent(student);
        record.setCheckedInAt(LocalDateTime.now());

        ResumptionAttendanceRecord saved = recordRepository.save(record);
        log.info("Resumption attendance marked: sessionPublicId={}, studentPublicId={}",
                session.getPublicId(), student.getPublicId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse getAttendanceSummary(User student) {
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException("You are not enrolled in any active cohort"));

        Cohort cohort = enrollment.getCohort();
        LocalDate since = enrollment.getEnrolledAt().toLocalDate();
        int[] stats = computeAttendance(student, cohort, since);
        int percent = stats[0] == 0 ? 0 : (int) Math.round((stats[1] * 100.0) / stats[0]);

        return AttendanceSummaryResponse.builder()
                .totalDays(stats[0])
                .daysPresent(stats[1])
                .daysAbsent(stats[2])
                .percentPresent(percent)
                .cohortName(cohort.getName())
                .build();
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceSummaryEntry> getCohortAttendanceSummary(UUID cohortPublicId, Institution institution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, institution);
        return cohortEnrollmentRepository.findByCohortAndIsActiveTrue(cohort).stream()
                .map(e -> {
                    User student = e.getStudent();
                    LocalDate since = e.getEnrolledAt().toLocalDate();
                    int[] stats = computeAttendance(student, cohort, since);
                    int rate = stats[0] == 0 ? 0 : (int) Math.round((stats[1] * 100.0) / stats[0]);
                    return StudentAttendanceSummaryEntry.builder()
                            .studentPublicId(student.getPublicId())
                            .studentName(student.getFullName())
                            .totalDays(stats[0])
                            .present(stats[1])
                            .absent(stats[2])
                            .ratePercent(rate)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CohortAttendanceOverviewEntry> getAllCohortsAttendanceOverview(Institution institution) {
        return cohortService.getAllByInstitution(institution).stream()
                .map(cohort -> {
                    List<CohortEnrollment> enrollments = cohortEnrollmentRepository.findByCohortAndIsActiveTrue(cohort);
                    int studentCount = enrollments.size();
                    int avgRate = 0;
                    if (studentCount > 0) {
                        int totalRate = 0;
                        for (CohortEnrollment e : enrollments) {
                            int[] stats = computeAttendance(e.getStudent(), cohort, e.getEnrolledAt().toLocalDate());
                            int rate = stats[0] == 0 ? 0 : (int) Math.round((stats[1] * 100.0) / stats[0]);
                            totalRate += rate;
                        }
                        avgRate = (int) Math.round(totalRate / (double) studentCount);
                    }
                    return CohortAttendanceOverviewEntry.builder()
                            .cohortPublicId(cohort.getPublicId())
                            .cohortName(cohort.getName())
                            .studentCount(studentCount)
                            .averageRatePercent(avgRate)
                            .build();
                })
                .toList();
    }

    // Returns [total, present, absent] for a single student in a cohort since their enrollment date.
    private int[] computeAttendance(User student, Cohort cohort, LocalDate since) {
        int total = resumptionSessionRepository.findByCohortSince(cohort, since).size();
        int present = (int) recordRepository.findByStudentAndCohort(student, cohort).stream()
                .map(r -> r.getResumptionSession().getPublicId())
                .collect(Collectors.toSet())
                .size();
        return new int[]{total, present, total - present};
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryEntry> getAttendanceHistory(User student) {
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException("You are not enrolled in any active cohort"));

        Cohort cohort = enrollment.getCohort();
        LocalDate since = enrollment.getEnrolledAt().toLocalDate();

        List<ResumptionSession> sessions = resumptionSessionRepository.findByCohortSince(cohort, since);

        Map<UUID, ResumptionAttendanceRecord> recordBySessionId = recordRepository
                .findByStudentAndCohort(student, cohort)
                .stream()
                .collect(Collectors.toMap(r -> r.getResumptionSession().getPublicId(), r -> r));

        return sessions.stream()
                .map(s -> {
                    ResumptionAttendanceRecord record = recordBySessionId.get(s.getPublicId());
                    return AttendanceHistoryEntry.builder()
                            .sessionPublicId(s.getPublicId())
                            .date(s.getResumptionDate())
                            .status(record != null ? "PRESENT" : "ABSENT")
                            .checkedInAt(record != null ? record.getCheckedInAt() : null)
                            .build();
                })
                .toList();
    }

    private ResumptionAttendanceRecordResponse toResponse(ResumptionAttendanceRecord record) {
        return ResumptionAttendanceRecordResponse.builder()
                .publicId(record.getPublicId())
                .resumptionSessionPublicId(record.getResumptionSession().getPublicId())
                .studentPublicId(record.getStudent().getPublicId())
                .checkedInAt(record.getCheckedInAt())
                .build();
    }
}
