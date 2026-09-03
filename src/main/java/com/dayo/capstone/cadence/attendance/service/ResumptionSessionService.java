package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionResponse;
import com.dayo.capstone.cadence.attendance.enums.SessionClosedBy;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumptionSessionService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ResumptionSessionRepository resumptionSessionRepository;

    public ResumptionSession findEntityByPublicId(UUID publicId) {
        return resumptionSessionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Resumption session not found: " + publicId));
    }

    public ResumptionSession findEntityByCode(String code) {
        return resumptionSessionRepository.findByResumptionCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid resumption code"));
    }

    // Tenant-scoped — same pattern as CohortService/SubjectService.
    public ResumptionSession findEntityByPublicIdScopedToInstitution(UUID publicId, Institution callerInstitution) {
        ResumptionSession session = findEntityByPublicId(publicId);
        if (!session.getCohort().getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Resumption session not found: " + publicId);
        }
        return session;
    }

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
        session.setQrToken(UUID.randomUUID().toString());
        ResumptionSession saved = resumptionSessionRepository.save(session);
        log.info("Resumption code regenerated: publicId={}, cohortPublicId={}",
                saved.getPublicId(), saved.getCohort().getPublicId());
        return toResponse(saved);
    }

    @Transactional
    public ResumptionSessionResponse closeManually(UUID publicId, Institution callerInstitution) {
        ResumptionSession session = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);
        session.setOpen(false);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(SessionClosedBy.MANAGEMENT);
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