package com.dayo.capstone.cadence.cohort.service;

import com.dayo.capstone.cadence.cohort.dto.request.CreateCohortRequest;
import com.dayo.capstone.cadence.cohort.dto.response.CohortResponse;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.repository.CohortRepository;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortService {

    private final CohortRepository cohortRepository;

    @Transactional
    public CohortResponse create(CreateCohortRequest request, Institution institution) {
        Cohort cohort = new Cohort();
        cohort.setInstitution(institution);
        cohort.setName(request.getName());
        cohort.setStartDate(request.getStartDate());
        cohort.setEndDate(request.getEndDate());

        Cohort saved = cohortRepository.save(cohort);
        log.info("Cohort created: publicId={}, name={}", saved.getPublicId(), saved.getName());

        return toResponse(saved);
    }

    public CohortResponse getByPublicId(UUID publicId) {
        return toResponse(findEntityByPublicId(publicId));
    }

    public List<CohortResponse> getAll() {
        return cohortRepository.findAll().stream().map(this::toResponse).toList();
    }

    // Unscoped lookup — used only where the caller's own institution
    // context isn't the relevant trust boundary (e.g. resolving a cohort
    // during self-registration, where the invite code itself already
    // proves the right to enroll, not the caller's session/role).
    public Cohort findEntityByPublicId(UUID publicId) {
        return cohortRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cohort not found: " + publicId));
    }

    // Tenant-scoped lookup — used anywhere a MANAGEMENT-or-below caller's
    // own institution must be enforced (PRD Section 18.1). Deliberately
    // throws the SAME "not found" message on a cross-tenant hit as on a
    // genuine miss — never reveal that a cohort exists at a DIFFERENT
    // institution, which would itself be an information leak.
    public Cohort findEntityByPublicIdScopedToInstitution(UUID cohortPublicId, Institution callerInstitution) {
        Cohort cohort = findEntityByPublicId(cohortPublicId);
        if (!cohort.getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Cohort not found: " + cohortPublicId);
        }
        return cohort;
    }

    private CohortResponse toResponse(Cohort cohort) {
        return CohortResponse.builder()
                .publicId(cohort.getPublicId())
                .institutionPublicId(cohort.getInstitution().getPublicId())
                .name(cohort.getName())
                .startDate(cohort.getStartDate())
                .endDate(cohort.getEndDate())
                .isActive(cohort.isActive())
                .build();
    }
}