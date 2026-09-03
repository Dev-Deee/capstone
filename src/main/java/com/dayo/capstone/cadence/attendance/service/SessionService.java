package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.OpenSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.SessionResponse;
import com.dayo.capstone.cadence.attendance.enums.SessionClosedBy;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.SessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.service.SubjectService;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SessionRepository sessionRepository;
    private final CohortService cohortService;
    private final SubjectService subjectService;
    private final FacilitatorCohortRepository facilitatorCohortRepository;

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
                .expiresAt(session.getExpiresAt())
                .isOpen(session.isCurrentlyOpen())
                .build();
    }
}