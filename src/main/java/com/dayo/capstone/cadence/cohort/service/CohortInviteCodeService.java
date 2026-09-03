package com.dayo.capstone.cadence.cohort.service;

import com.dayo.capstone.cadence.cohort.dto.request.CreateInviteCodeRequest;
import com.dayo.capstone.cadence.cohort.dto.response.InviteCodeResponse;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortInviteCode;
import com.dayo.capstone.cadence.cohort.repository.CohortInviteCodeRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
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
public class CohortInviteCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 — avoids ambiguity when read aloud or handwritten

    private final CohortInviteCodeRepository inviteCodeRepository;
    private final CohortService cohortService;

    @Transactional
    public InviteCodeResponse create(CreateInviteCodeRequest request, Institution callerInstitution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getCohortPublicId()), callerInstitution);

        CohortInviteCode invite = new CohortInviteCode();
        invite.setCohort(cohort);
        invite.setCode(generateCode());
        invite.setMaxUses(request.getMaxUses() != null ? request.getMaxUses() : 0);
        invite.setExpiresAt(request.getExpiresInDays() != null
                ? LocalDateTime.now().plusDays(request.getExpiresInDays())
                : null);

        CohortInviteCode saved = inviteCodeRepository.save(invite);
        log.info("Invite code generated: cohortPublicId={}, code={}", cohort.getPublicId(), saved.getCode());

        return InviteCodeResponse.builder()
                .publicId(saved.getPublicId())
                .code(saved.getCode())
                .cohortPublicId(cohort.getPublicId())
                .expiresAt(saved.getExpiresAt())
                .maxUses(saved.getMaxUses())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}