package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.ManualAttendanceOverrideRequest;
import com.dayo.capstone.cadence.attendance.dto.request.MarkAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.dto.response.BiometricChallenge;
import com.dayo.capstone.cadence.attendance.dto.response.SessionAttendanceHistoryEntry;
import com.dayo.capstone.cadence.attendance.repository.SessionRepository;
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
import com.dayo.capstone.cadence.security.dto.request.WebAuthnAuthenticationRequest;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.AttendanceOverriddenEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final TotpService totpService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final CheckInBiometricService checkInBiometricService;
    private final DomainEvents domainEvents;

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

        var existing = attendanceRecordRepository.findBySessionAndStudent(session, student);
        if (existing.isPresent()) {
            // Code accepted earlier but the biometric step never finished (a
            // dropped connection, a closed tab): re-entering the code resumes it.
            if (existing.get().getStatus() == AttendanceStatus.PENDING_BIOMETRIC) {
                return toResponse(existing.get(), biometricChallenge(existing.get(), student));
            }
            throw new IllegalArgumentException("Attendance already marked for this session");
        }

        // PRD Section 6.12: a webAuthnCapable student's check-in is incomplete
        // until they confirm on-device. The record is created now — holding the
        // slot, so a network blip during the biometric step never costs them the
        // correct code — but it doesn't count as attendance until confirmed.
        boolean biometric = checkInBiometricService.isRequiredFor(student);
        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setStatus(biometric ? AttendanceStatus.PENDING_BIOMETRIC : AttendanceStatus.PRESENT);
        record.setVerificationMethod(VerificationMethod.CODE);
        record.setWebAuthnVerified(biometric ? Boolean.FALSE : null);
        record.setSubmittedAt(LocalDateTime.now());

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        log.info("Attendance marked: sessionPublicId={}, studentPublicId={}, status={}",
                session.getPublicId(), student.getPublicId(), saved.getStatus());

        return toResponse(saved, biometric ? biometricChallenge(saved, student) : null);
    }

    // A fresh single-use challenge for a check-in awaiting biometric confirmation —
    // the retry path after a cancelled prompt or a dropped connection, no code needed.
    @Transactional(readOnly = true)
    public BiometricChallenge reissueBiometricChallenge(UUID recordPublicId, User student) {
        return biometricChallenge(pendingRecord(recordPublicId, student), student);
    }

    // Completes a pending check-in. Only a verified assertion from one of the
    // student's own credentials sets webAuthnVerified = true; any failure
    // throws, and the record stays pending (webAuthnVerified = false) for a
    // retry or the facilitator's manual override.
    @Transactional
    public AttendanceRecordResponse confirmBiometric(
            UUID recordPublicId, WebAuthnAuthenticationRequest assertion, User student) {
        AttendanceRecord record = pendingRecord(recordPublicId, student);
        checkInBiometricService.verify(challengeKey(record), student, assertion);
        record.setStatus(AttendanceStatus.PRESENT);
        record.setWebAuthnVerified(true);
        AttendanceRecord saved = attendanceRecordRepository.save(record);
        log.info("Attendance biometric confirmed: sessionPublicId={}, studentPublicId={}",
                record.getSession().getPublicId(), student.getPublicId());
        return toResponse(saved);
    }

    private AttendanceRecord pendingRecord(UUID recordPublicId, User student) {
        AttendanceRecord record = attendanceRecordRepository.findByPublicIdAndStudent(recordPublicId, student)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found: " + recordPublicId));
        if (record.getStatus() != AttendanceStatus.PENDING_BIOMETRIC) {
            throw new IllegalArgumentException("This check-in is not awaiting biometric confirmation");
        }
        Session session = record.getSession();
        checkInBiometricService.requireWithinGrace(session.isCurrentlyOpen(), session.getClosedAt(), session.getExpiresAt());
        return record;
    }

    private BiometricChallenge biometricChallenge(AttendanceRecord record, User student) {
        return checkInBiometricService.challenge(challengeKey(record), student);
    }

    private static String challengeKey(AttendanceRecord record) {
        return "attendance:session:" + record.getPublicId();
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

        if (!request.getStatus().isOverrideOutcome()) {
            throw new IllegalArgumentException("Status must be PRESENT, LATE or ABSENT");
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

        domainEvents.publish(new AttendanceOverriddenEvent(
                callerInstitution.getId(), caller.getId(), student.getId(), AttendanceOverriddenEvent.Kind.CLASS,
                session.getSubject().getName(), session.getCreatedAt().toLocalDate(), saved.getStatus().name()));
        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.OVERRIDE,
                "AttendanceRecord", saved.getPublicId(), beforeState,
                "{\"status\":\"" + saved.getStatus() + "\",\"verificationMethod\":\"MANUAL_OVERRIDE\"}");
        log.info("Attendance manually overridden: sessionPublicId={}, studentPublicId={}, status={}, by={}",
                sessionPublicId, studentPublicId, saved.getStatus(), caller.getPublicId());

        return toResponse(saved);
    }

    // The student's own class-session history: the facilitator roster's left join
    // (sessions × this student's records) for one student instead of a whole cohort.
    // Sessions before enrollment are excluded, matching the resumption history.
    @Transactional(readOnly = true)
    public List<SessionAttendanceHistoryEntry> getSessionHistory(User student) {
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException("You are not enrolled in any active cohort"));
        Cohort cohort = enrollment.getCohort();

        Map<Long, AttendanceRecord> bySessionId = attendanceRecordRepository
                .findByStudentAndSession_Cohort(student, cohort).stream()
                .collect(Collectors.toMap(r -> r.getSession().getId(), r -> r));

        return sessionRepository.findByCohortSince(cohort, enrollment.getEnrolledAt().toLocalDate().atStartOfDay())
                .stream()
                .map(s -> {
                    AttendanceRecord record = bySessionId.get(s.getId());
                    String status = record != null ? record.getStatus().name()
                            : s.isCurrentlyOpen() ? "OPEN" : "ABSENT";
                    return SessionAttendanceHistoryEntry.builder()
                            .sessionPublicId(s.getPublicId())
                            .date(s.getCreatedAt().toLocalDate())
                            .subjectName(s.getSubject().getName())
                            .status(status)
                            .markedAt(record != null ? record.getSubmittedAt() : null)
                            .build();
                })
                .toList();
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return toResponse(record, null);
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record, BiometricChallenge biometric) {
        return AttendanceRecordResponse.builder()
                .publicId(record.getPublicId())
                .sessionPublicId(record.getSession().getPublicId())
                .studentPublicId(record.getStudent().getPublicId())
                .status(record.getStatus())
                .verificationMethod(record.getVerificationMethod())
                .submittedAt(record.getSubmittedAt())
                .webAuthnVerified(record.getWebAuthnVerified())
                .biometric(biometric)
                .build();
    }
}
