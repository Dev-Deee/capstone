package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.attendance.dto.request.OpenResumptionSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.CurrentCodeResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRosterEntry;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionQrRouteResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionResponse;
import com.dayo.capstone.cadence.attendance.enums.SessionClosedBy;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumptionSessionService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ResumptionSessionRepository resumptionSessionRepository;
    private final CohortService cohortService;
    private final TotpService totpService;
    private final AdminPermissionService adminPermissionService;
    private final ResumptionAttendanceRecordRepository resumptionAttendanceRecordRepository;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public ResumptionSessionResponse open(OpenResumptionSessionRequest request, User openedBy, Institution institution) {
        // FacilitatorCohort assignment check deliberately removed — any
        // facilitator at the institution may open resumption for any cohort
        // at that institution, regardless of whether they teach it.
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getCohortPublicId()), institution);

        LocalDate today = LocalDate.now();
        resumptionSessionRepository.findByCohortAndResumptionDate(cohort, today)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "A resumption session for this cohort already exists today");
                });

        ResumptionSession session = new ResumptionSession();
        session.setCohort(cohort);
        session.setResumptionDate(today);
        session.setResumptionCode(generateCode());
        session.setCodeSecret(totpService.generateSecret());
        session.setQrToken(UUID.randomUUID().toString());
        session.setOpensAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(request.getExpiryMinutes()));

        ResumptionSession saved = resumptionSessionRepository.save(session);
        log.info("Resumption session opened: cohortPublicId={}, openedByPublicId={}, publicId={}",
                cohort.getPublicId(), openedBy.getPublicId(), saved.getPublicId());

        return toResponse(saved);
    }

    public ResumptionSession findEntityByPublicId(UUID publicId) {
        return resumptionSessionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Resumption session not found: " + publicId));
    }

    public ResumptionSession findEntityByCode(String code) {
        return resumptionSessionRepository.findByResumptionCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid resumption code"));
    }

    // Public — used by ResumptionAttendanceRecordService: find all currently-open
    // resumption sessions for a cohort so TOTP can be validated against each.
    public List<ResumptionSession> findOpenResumptionSessionsByCohort(Cohort cohort) {
        return resumptionSessionRepository.findOpenByCohort(cohort);
    }

    // Returns the live TOTP code + countdown. @Transactional keeps the Hibernate session
    // open so the lazy cohort/institution associations (used by the institution-scope check
    // and the permission check) can be loaded — OSIV is disabled in application.properties.
    @Transactional(readOnly = true)
    public CurrentCodeResponse getCurrentCode(UUID publicId, Institution callerInstitution, User caller) {
        ResumptionSession session = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);

        if (!caller.hasRole(Role.SUPER_ADMIN) && !caller.hasRole(Role.FACILITATOR)) {
            adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_RESUMPTION_CODES, session.getCohort());
        }

        if (session.getCodeSecret() == null) {
            throw new IllegalStateException("Session predates TOTP and has no code secret");
        }
        return CurrentCodeResponse.builder()
                .code(totpService.currentCode(session.getCodeSecret(), TotpService.PERIOD_SECONDS))
                .secondsRemaining(totpService.secondsRemainingInWindow(TotpService.PERIOD_SECONDS))
                .build();
    }

    // Public — used by the QR-route endpoint (unauthenticated). @Transactional
    // because the response reads the lazy cohort (OSIV is disabled).
    @Transactional(readOnly = true)
    public ResumptionSessionQrRouteResponse getQrRouteInfo(String qrToken) {
        ResumptionSession session = resumptionSessionRepository.findByQrToken(qrToken)
                .filter(ResumptionSession::isCurrentlyOpen)
                .orElseThrow(() -> new ResourceNotFoundException("Resumption session not found"));
        return ResumptionSessionQrRouteResponse.builder()
                .resumptionSessionPublicId(session.getPublicId())
                .cohortName(session.getCohort().getName())
                .isOpen(true)
                .build();
    }

    // Tenant-scoped — same pattern as CohortService/SubjectService.
    // @Transactional: reads the lazy cohort, and controllers call this directly.
    @Transactional(readOnly = true)
    public ResumptionSession findEntityByPublicIdScopedToInstitution(UUID publicId, Institution callerInstitution) {
        ResumptionSession session = findEntityByPublicId(publicId);
        if (!session.getCohort().getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Resumption session not found: " + publicId);
        }
        return session;
    }

    // @Transactional: toResponse() reads the lazy cohort (OSIV is disabled).
    @Transactional(readOnly = true)
    public List<ResumptionSessionResponse> getTodaysSessions(Institution institution) {
        LocalDate today = LocalDate.now();
        return resumptionSessionRepository.findByCohort_InstitutionAndResumptionDate(institution, today)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ResumptionSessionResponse regenerateCode(UUID publicId, Institution callerInstitution) {
        ResumptionSession session = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);
        session.setResumptionCode(generateCode());
        // codeSecret and qrToken are intentionally NOT regenerated here — they are stable
        // pointers to this session record for its entire lifetime (set once in open()).
        ResumptionSession saved = resumptionSessionRepository.save(session);
        log.info("Resumption code regenerated: publicId={}, cohortPublicId={}",
                saved.getPublicId(), saved.getCohort().getPublicId());
        return toResponse(saved);
    }

    @Transactional
    public ResumptionSessionResponse closeManually(UUID publicId, Institution callerInstitution, User caller) {
        ResumptionSession session = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);
        session.setOpen(false);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(SessionClosedBy.forCaller(caller));
        ResumptionSession saved = resumptionSessionRepository.save(session);
        log.info("Resumption session closed manually: publicId={}", saved.getPublicId());
        return toResponse(saved);
    }

    @Transactional
    public ResumptionSessionResponse adjustWindow(UUID publicId, LocalDateTime newExpiresAt, Institution callerInstitution) {
        ResumptionSession session = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);
        session.setExpiresAt(newExpiresAt);
        ResumptionSession saved = resumptionSessionRepository.save(session);
        log.info("Resumption window adjusted: publicId={}, newExpiresAt={}", saved.getPublicId(), newExpiresAt);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ResumptionSessionResponse> getTodayForFacilitator(Institution institution) {
        LocalDate today = LocalDate.now();
        return resumptionSessionRepository.findByResumptionDateAndCohort_Institution(today, institution).stream()
                .map(this::toResponse)
                .toList();
    }

    // All resumption sessions for a cohort, newest first — used by the attendance
    // history page. @Transactional needed for toResponse()'s lazy cohort access.
    @Transactional(readOnly = true)
    public List<ResumptionSessionResponse> getSessionsByCohort(UUID cohortPublicId, Institution institution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, institution);
        return resumptionSessionRepository.findByCohortOrderByResumptionDateDesc(cohort)
                .stream().map(this::toResponse).toList();
    }

    // LEFT JOIN of enrolled students against resumption attendance records.
    // @Transactional needed for lazy cohort/student association access (OSIV disabled).
    @Transactional(readOnly = true)
    public List<ResumptionAttendanceRosterEntry> getAttendanceRoster(UUID publicId, Institution callerInstitution) {
        ResumptionSession session = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);
        Map<Long, ResumptionAttendanceRecord> byStudentId = resumptionAttendanceRecordRepository
                .findByResumptionSession(session)
                .stream().collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r));
        return cohortEnrollmentRepository.findByCohortAndIsActiveTrue(session.getCohort())
                .stream()
                .map(e -> {
                    User student = e.getStudent();
                    ResumptionAttendanceRecord record = byStudentId.get(student.getId());
                    return ResumptionAttendanceRosterEntry.builder()
                            .studentPublicId(student.getPublicId())
                            .studentName(student.getFullName())
                            .status(record != null ? record.getEffectiveStatus().name() : "ABSENT")
                            .markedAt(record != null ? record.getCheckedInAt() : null)
                            .matchedInstitutionNetwork(record != null ? record.getMatchedInstitutionNetwork() : null)
                            .webAuthnVerified(record != null ? record.getWebAuthnVerified() : null)
                            .confidenceScore(record != null ? record.getConfidenceScore() : null)
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

    private ResumptionSessionResponse toResponse(ResumptionSession session) {
        return ResumptionSessionResponse.builder()
                .publicId(session.getPublicId())
                .cohortPublicId(session.getCohort().getPublicId())
                .resumptionCode(session.getResumptionCode())
                .qrToken(session.getQrToken())
                .opensAt(session.getOpensAt())
                .expiresAt(session.getExpiresAt())
                .isOpen(session.isCurrentlyOpen())
                .build();
    }
}
