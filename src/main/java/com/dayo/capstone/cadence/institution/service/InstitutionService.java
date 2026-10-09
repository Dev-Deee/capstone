package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.institution.dto.response.InstitutionResponse;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository institutionRepository;

    @Transactional(readOnly = true)
    public InstitutionResponse getByPublicId(UUID publicId) {
        return toResponse(findEntityByPublicId(publicId));
    }

    @Transactional(readOnly = true)
    public List<InstitutionResponse> getAll() {
        return institutionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public Institution findEntityByPublicId(UUID publicId) {
        return institutionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + publicId));
    }

    @Transactional(readOnly = true)
    public Institution findEntityByUserPublicId(UUID userPublicId) {
        TenantContext.Scope scope = TenantContext.get();
        if (scope == null || !scope.userPublicId().equals(userPublicId)) {
            throw new AccessDeniedException("No institution context for this request");
        }
        if (scope.institutionId() == null) {
            throw new AccessDeniedException("Choose an institution to work in first");
        }
        return institutionRepository.findById(scope.institutionId())
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found"));
    }

    public InstitutionResponse toResponse(Institution institution) {
        return InstitutionResponse.builder()
                .publicId(institution.getPublicId())
                .name(institution.getName())
                .slug(institution.getSlug())
                .status(institution.getStatus())
                .address(institution.getAddress())
                .guardianLinkageMode(institution.getGuardianLinkageMode())
                .guardianNotificationDefault(institution.getGuardianNotificationDefault())
                .createdAt(institution.getCreatedAt())
                .build();
    }
}
