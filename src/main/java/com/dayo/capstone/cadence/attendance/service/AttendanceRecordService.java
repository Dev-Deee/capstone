package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.ManualAttendanceOverrideRequest;
import com.dayo.capstone.cadence.attendance.dto.request.MarkAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SessionService sessionService;
    private final TotpService totpService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Transactional
    public AttendanceRecordResponse markAttendance(MarkAttendanceRequest request, User student) {
        // Resolve the student's active cohort — enrollment is the real gate
        // (Section 18.2). A valid code from a different cohort's session must
        // not let a student mark attendance against a session they have no
        // business attending.
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not enrolled in any active cohort"));

        Cohort cohort = enrollment.getCohort();

        // Find all currently-open sessions for this cohort, then TOTP-validate.
        // There will normally be at most one open session per cohort at a time,
        // but we check all to handle any overlap edge case — first match wins.
        List<Session> candidates = sessionService.findOpenSessionsByCohort(cohort);
        Session session = candidates.stream()
                .filter(s -> totpService.isValid(s.getCodeSecret(), request.getSessionCode(), TotpService.PERIOD_SECONDS))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Session code is invalid or has expired"));

        attendanceRecordRepository.findBySessionAndStudent(session, student)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Attendance already marked for this session");
                });

        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setStatus(AttendanceStatus.PRESENT);
        record.setVerificationMethod(VerificationMethod.CODE);
        record.setSubmittedAt(LocalDateTime.now());

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        log.info("Attendance marked: sessionPublicId={}, studentPublicId={}",
                session.getPublicId(), student.getPublicId());

        return toResponse(saved);
    }

    // PRD Section 6.6 — manual override, distinct code path from normal
    // verification, mandatory reason recorded. Same ownership rule as the
    // session roster: the facilitator who opened the session, or SUPER_ADMIN.
    @Transactional
    public AttendanceRecordResponse manualOverride(
            UUID sessionPublicId, UUID studentPublicId,
            ManualAttendanceOverrideRequest request, User caller, Institution callerInstitution
    ) {
        Session session = sessionService.findEntityByPublicId(sessionPublicId);
        if (!session.getCohort().getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Session not found: " + sessionPublicId);
        }
        if (!caller.hasRole(Role.SUPER_ADMIN)
                && !session.getFacilitator().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Only the facilitator who opened this session or a SUPER_ADMIN may override attendance");
        }

        User student = userService.findEntityByPublicId(studentPublicId);
        cohortEnrollmentRepository.findByStudentAndCohort(student, session.getCohort())
                .filter(CohortEnrollment::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Student is not actively enrolled in this session's cohort"));

        AttendanceRecord record = attendanceRecordRepository.findBySessionAndStudent(session, student)
                .orElseGet(() -> {
                    AttendanceRecord fresh = new AttendanceRecord();
                    fresh.setSession(session);
                    fresh.setStudent(student);
                    return fresh;
                });
        String beforeState = record.getStatus() == null ? null
                : "{\"status\":\"" + record.getStatus() + "\",\"verificationMethod\":\"" + record.getVerificationMethod() + "\"}";

        record.setStatus(request.getStatus());
        record.setVerificationMethod(VerificationMethod.MANUAL_OVERRIDE);
        record.setOverrideReason(request.getReason());
        record.setSubmittedAt(LocalDateTime.now());
        AttendanceRecord saved = attendanceRecordRepository.save(record);

        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.OVERRIDE,
                "AttendanceRecord", saved.getPublicId(), beforeState,
                "{\"status\":\"" + saved.getStatus() + "\",\"verificationMethod\":\"MANUAL_OVERRIDE\"}");
        log.info("Attendance manually overridden: sessionPublicId={}, studentPublicId={}, status={}, by={}",
                sessionPublicId, studentPublicId, saved.getStatus(), caller.getPublicId());

        return toResponse(saved);
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return AttendanceRecordResponse.builder()
                .publicId(record.getPublicId())
                .sessionPublicId(record.getSession().getPublicId())
                .studentPublicId(record.getStudent().getPublicId())
                .status(record.getStatus())
                .verificationMethod(record.getVerificationMethod())
                .submittedAt(record.getSubmittedAt())
                .build();
    }
}
