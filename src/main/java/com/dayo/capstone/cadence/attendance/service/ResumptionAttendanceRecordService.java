package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.attendance.dto.request.ManualAttendanceOverrideRequest;
import com.dayo.capstone.cadence.attendance.dto.request.MarkResumptionAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceHistoryEntry;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceSummaryResponse;
import com.dayo.capstone.cadence.attendance.dto.response.BiometricChallenge;
import com.dayo.capstone.cadence.attendance.dto.response.CohortAttendanceOverviewEntry;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.dto.response.StudentAttendanceSummaryEntry;
import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.security.dto.request.WebAuthnAuthenticationRequest;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.AttendanceOverriddenEvent;

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
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;
    private final AuditLogService auditLogService;
    private final CheckInBiometricService checkInBiometricService;
    private final DomainEvents domainEvents;

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

        var existing = recordRepository.findByResumptionSessionAndStudent(session, student);
        if (existing.isPresent()) {
            // Code accepted earlier, biometric step unfinished — re-entering the code resumes it.
            if (existing.get().getEffectiveStatus() == AttendanceStatus.PENDING_BIOMETRIC) {
                return toResponse(existing.get(), biometricChallenge(existing.get(), student));
            }
            throw new IllegalArgumentException("Resumption attendance already marked for today");
        }

        // PRD Section 6.12 — same rule as class attendance (AttendanceRecordService).
        boolean biometric = checkInBiometricService.isRequiredFor(student);
        ResumptionAttendanceRecord record = new ResumptionAttendanceRecord();
        record.setResumptionSession(session);
        record.setStudent(student);
        record.setCheckedInAt(LocalDateTime.now());
        record.setStatus(biometric ? AttendanceStatus.PENDING_BIOMETRIC : AttendanceStatus.PRESENT);
        record.setVerificationMethod(VerificationMethod.CODE);
        record.setWebAuthnVerified(biometric ? Boolean.FALSE : null);

        ResumptionAttendanceRecord saved = recordRepository.save(record);
        log.info("Resumption attendance marked: sessionPublicId={}, studentPublicId={}, status={}",
                session.getPublicId(), student.getPublicId(), saved.getStatus());

        return toResponse(saved, biometric ? biometricChallenge(saved, student) : null);
    }

    @Transactional(readOnly = true)
    public BiometricChallenge reissueBiometricChallenge(UUID recordPublicId, User student) {
        return biometricChallenge(pendingRecord(recordPublicId, student), student);
    }

    @Transactional
    public ResumptionAttendanceRecordResponse confirmBiometric(
            UUID recordPublicId, WebAuthnAuthenticationRequest assertion, User student) {
        ResumptionAttendanceRecord record = pendingRecord(recordPublicId, student);
        checkInBiometricService.verify(challengeKey(record), student, assertion);
        record.setStatus(AttendanceStatus.PRESENT);
        record.setWebAuthnVerified(true);
        ResumptionAttendanceRecord saved = recordRepository.save(record);
        log.info("Resumption attendance biometric confirmed: sessionPublicId={}, studentPublicId={}",
                record.getResumptionSession().getPublicId(), student.getPublicId());
        return toResponse(saved);
    }

    private ResumptionAttendanceRecord pendingRecord(UUID recordPublicId, User student) {
        ResumptionAttendanceRecord record = recordRepository.findByPublicIdAndStudent(recordPublicId, student)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found: " + recordPublicId));
        if (record.getEffectiveStatus() != AttendanceStatus.PENDING_BIOMETRIC) {
            throw new IllegalArgumentException("This check-in is not awaiting biometric confirmation");
        }
        ResumptionSession session = record.getResumptionSession();
        checkInBiometricService.requireWithinGrace(session.isCurrentlyOpen(), session.getClosedAt(), session.getExpiresAt());
        return record;
    }

    private BiometricChallenge biometricChallenge(ResumptionAttendanceRecord record, User student) {
        return checkInBiometricService.challenge(challengeKey(record), student);
    }

    private static String challengeKey(ResumptionAttendanceRecord record) {
        return "attendance:resumption:" + record.getPublicId();
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
    public List<CohortAttendanceOverviewEntry> getAllCohortsAttendanceOverview(
            Institution institution, Predicate<Cohort> visible) {
        return cohortService.getAllByInstitution(institution).stream()
                .filter(visible)
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
                .filter(r -> r.getEffectiveStatus().countsAsAttended())
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
                            .status(record != null ? record.getEffectiveStatus().name() : "ABSENT")
                            .checkedInAt(record != null ? record.getCheckedInAt() : null)
                            .build();
                })
                .toList();
    }

    // PRD Section 6.6 — manual override for campus resumption attendance.
    // Resumption sessions have no owning facilitator, so this follows the same
    // rule as close/regenerate: SUPER_ADMIN, or MANAGE_RESUMPTION_CODES for the cohort.
    @Transactional
    public ResumptionAttendanceRecordResponse manualOverride(
            UUID resumptionSessionPublicId, UUID studentPublicId,
            ManualAttendanceOverrideRequest request, User caller, Institution callerInstitution
    ) {
        ResumptionSession session = resumptionSessionService
                .findEntityByPublicIdScopedToInstitution(resumptionSessionPublicId, callerInstitution);
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_RESUMPTION_CODES, session.getCohort());
        }
        if (!request.getStatus().isOverrideOutcome()) {
            throw new IllegalArgumentException("Status must be PRESENT, LATE or ABSENT");
        }

        User student = userService.findEntityByPublicId(studentPublicId);
        cohortEnrollmentRepository.findByStudentAndCohort(student, session.getCohort())
                .filter(CohortEnrollment::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Student is not actively enrolled in this session's cohort"));

        ResumptionAttendanceRecord record = recordRepository.findByResumptionSessionAndStudent(session, student)
                .orElseGet(() -> {
                    ResumptionAttendanceRecord fresh = new ResumptionAttendanceRecord();
                    fresh.setResumptionSession(session);
                    fresh.setStudent(student);
                    return fresh;
                });
        String beforeState = record.getId() == null ? null
                : "{\"status\":\"" + record.getEffectiveStatus() + "\",\"verificationMethod\":\""
                  + (record.getVerificationMethod() != null ? record.getVerificationMethod() : VerificationMethod.CODE) + "\"}";

        record.setStatus(request.getStatus());
        record.setVerificationMethod(VerificationMethod.MANUAL_OVERRIDE);
        record.setOverrideReason(request.getReason());
        record.setCheckedInAt(LocalDateTime.now());
        ResumptionAttendanceRecord saved = recordRepository.save(record);

        domainEvents.publish(new AttendanceOverriddenEvent(
                callerInstitution.getId(), caller.getId(), student.getId(), AttendanceOverriddenEvent.Kind.RESUMPTION,
                "Campus resumption", session.getResumptionDate(), saved.getEffectiveStatus().name()));
        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.OVERRIDE,
                "ResumptionAttendanceRecord", saved.getPublicId(), beforeState,
                "{\"status\":\"" + saved.getStatus() + "\",\"verificationMethod\":\"MANUAL_OVERRIDE\"}");
        log.info("Resumption attendance manually overridden: sessionPublicId={}, studentPublicId={}, status={}, by={}",
                resumptionSessionPublicId, studentPublicId, saved.getStatus(), caller.getPublicId());

        return toResponse(saved);
    }

    private ResumptionAttendanceRecordResponse toResponse(ResumptionAttendanceRecord record) {
        return toResponse(record, null);
    }

    private ResumptionAttendanceRecordResponse toResponse(ResumptionAttendanceRecord record, BiometricChallenge biometric) {
        return ResumptionAttendanceRecordResponse.builder()
                .publicId(record.getPublicId())
                .resumptionSessionPublicId(record.getResumptionSession().getPublicId())
                .studentPublicId(record.getStudent().getPublicId())
                .checkedInAt(record.getCheckedInAt())
                .status(record.getEffectiveStatus().name())
                .webAuthnVerified(record.getWebAuthnVerified())
                .biometric(biometric)
                .build();
    }
}
