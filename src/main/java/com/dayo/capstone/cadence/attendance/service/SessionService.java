package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.OpenSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.CurrentCodeResponse;
import com.dayo.capstone.cadence.attendance.dto.response.SessionAttendanceRosterEntry;
import com.dayo.capstone.cadence.attendance.dto.response.SessionQrRouteResponse;
import com.dayo.capstone.cadence.attendance.dto.response.SessionResponse;
import com.dayo.capstone.cadence.attendance.enums.SessionClosedBy;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.SessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.service.SubjectService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SessionRepository sessionRepository;
    private final CohortService cohortService;
    private final SubjectService subjectService;
    private final FacilitatorCohortRepository facilitatorCohortRepository;
    private final TotpService totpService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public SessionResponse open(OpenSessionRequest request, User facilitator, Institution callerInstitution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getCohortPublicId()), callerInstitution);
        Subject subject = subjectService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getSubjectPublicId()), callerInstitution);

        // A facilitator may only open a session for a subject+cohort
        // they're actually assigned to (FacilitatorCohort, Section 4.4) —
        // not any subject in any cohort at the institution.
        FacilitatorCohort assignment = facilitatorCohortRepository
                .findByFacilitatorAndCohortAndSubject(facilitator, cohort, subject)
                .filter(FacilitatorCohort::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not assigned to teach this subject in this cohort"));

        Session session = new Session();
        session.setCohort(cohort);
        session.setSubject(subject);
        session.setFacilitator(facilitator);
        session.setSessionCode(generateCode());
        session.setCodeSecret(totpService.generateSecret());
        session.setQrToken(java.util.UUID.randomUUID().toString());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(request.getExpiryMinutes()));

        Session saved = sessionRepository.save(session);
        log.info("Session opened: publicId={}, cohortPublicId={}, subjectPublicId={}, facilitatorPublicId={}",
                saved.getPublicId(), cohort.getPublicId(), subject.getPublicId(), facilitator.getPublicId());

        return toResponse(saved);
    }

    @Transactional
    public SessionResponse close(UUID sessionPublicId, User requestingFacilitator) {
        Session session = findEntityByPublicId(sessionPublicId);

        if (!session.getFacilitator().getId().equals(requestingFacilitator.getId())) {
            throw new IllegalArgumentException("Only the facilitator who opened this session may close it");
        }

        session.setOpen(false);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(SessionClosedBy.FACILITATOR);
        Session saved = sessionRepository.save(session);

        log.info("Session closed manually: publicId={}", saved.getPublicId());
        return toResponse(saved);
    }

    // Public — needed by AttendanceRecordService for cross-module lookup.
    public Session findEntityByPublicId(UUID publicId) {
        return sessionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + publicId));
    }

    // Public — the attendance-marking flow resolves a session by the code
    // a student enters, not by publicId (the student never sees that).
    public Session findEntityBySessionCode(String code) {
        return sessionRepository.findBySessionCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid session code"));
    }

    // Public — used by AttendanceRecordService: find all currently-open
    // sessions for a cohort so TOTP can be validated against each.
    public List<Session> findOpenSessionsByCohort(Cohort cohort) {
        return sessionRepository.findOpenByCohort(cohort);
    }

    // Facilitator-only — returns the live TOTP code and countdown.
    // Never cached; caller must re-hit to get a fresh value.
    public CurrentCodeResponse getCurrentCode(UUID sessionPublicId, User requestingFacilitator) {
        Session session = findEntityByPublicId(sessionPublicId);
        if (!session.getFacilitator().getId().equals(requestingFacilitator.getId())) {
            throw new IllegalArgumentException("Only the facilitator who opened this session may view the live code");
        }
        if (session.getCodeSecret() == null) {
            throw new IllegalStateException("Session predates TOTP and has no code secret");
        }
        return CurrentCodeResponse.builder()
                .code(totpService.currentCode(session.getCodeSecret(), TotpService.PERIOD_SECONDS))
                .secondsRemaining(totpService.secondsRemainingInWindow(TotpService.PERIOD_SECONDS))
                .build();
    }

    // Public — used by the QR-route endpoint (unauthenticated).
    // Returns 404 for both "never existed" and "closed" — no information
    // leakage about whether the session ever existed. @Transactional because the
    // response reads the lazy cohort/subject (OSIV is disabled).
    @Transactional(readOnly = true)
    public SessionQrRouteResponse getQrRouteInfo(String qrToken) {
        Session session = sessionRepository.findByQrToken(qrToken)
                .filter(Session::isCurrentlyOpen)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        return SessionQrRouteResponse.builder()
                .sessionPublicId(session.getPublicId())
                .cohortName(session.getCohort().getName())
                .subjectName(session.getSubject().getName())
                .isOpen(true)
                .build();
    }

    // Lists sessions for a cohort+subject. Facilitators see only their own sessions;
    // SUPER_ADMIN sees all. @Transactional needed because toResponse() touches lazy
    // cohort/subject/facilitator associations (OSIV disabled).
    @Transactional(readOnly = true)
    public List<SessionResponse> getSessionsByCohortAndSubject(
            UUID cohortPublicId, UUID subjectPublicId, Institution institution, User caller) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, institution);
        Subject subject = subjectService.findEntityByPublicIdScopedToInstitution(subjectPublicId, institution);
        List<Session> sessions = caller.hasRole(Role.SUPER_ADMIN)
                ? sessionRepository.findByCohortAndSubjectOrderByCreatedAtDesc(cohort, subject)
                : sessionRepository.findByFacilitatorAndCohortAndSubjectOrderByCreatedAtDesc(caller, cohort, subject);
        return sessions.stream().map(this::toResponse).toList();
    }

    // LEFT JOIN of enrolled students against attendance records for this session.
    // @Transactional needed for lazy session.getFacilitator() and enrollment.getStudent().
    @Transactional(readOnly = true)
    public List<SessionAttendanceRosterEntry> getAttendanceRoster(UUID sessionPublicId, User caller) {
        Session session = findEntityByPublicId(sessionPublicId);
        if (!caller.hasRole(Role.SUPER_ADMIN)
                && !session.getFacilitator().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Only the facilitator who opened this session may view attendance");
        }
        Map<Long, AttendanceRecord> byStudentId = attendanceRecordRepository.findBySession(session)
                .stream().collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r));
        return cohortEnrollmentRepository.findByCohortAndIsActiveTrue(session.getCohort())
                .stream()
                .map(e -> {
                    User student = e.getStudent();
                    AttendanceRecord record = byStudentId.get(student.getId());
                    return SessionAttendanceRosterEntry.builder()
                            .studentPublicId(student.getPublicId())
                            .studentName(student.getFullName())
                            .status(record != null ? record.getStatus().name() : "ABSENT")
                            .markedAt(record != null ? record.getSubmittedAt() : null)
                            .verificationMethod(record != null ? record.getVerificationMethod().name() : null)
                            .build();
                })
                .toList();
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private SessionResponse toResponse(Session session) {
        return SessionResponse.builder()
                .publicId(session.getPublicId())
                .cohortPublicId(session.getCohort().getPublicId())
                .subjectPublicId(session.getSubject().getPublicId())
                .facilitatorPublicId(session.getFacilitator().getPublicId())
                .sessionCode(session.getSessionCode())
                .qrToken(session.getQrToken())
                .expiresAt(session.getExpiresAt())
                .isOpen(session.isCurrentlyOpen())
                .build();
    }
}