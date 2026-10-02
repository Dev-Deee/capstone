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

    // Every lookup from outside this service uses publicId — never the
    // internal Long id.
    @Transactional(readOnly = true)
    public InstitutionResponse getByPublicId(UUID publicId) {
        return toResponse(findEntityByPublicId(publicId));
    }

    // Platform-admin only (enforced in the controller): every tenant.
    @Transactional(readOnly = true)
    public List<InstitutionResponse> getAll() {
        return institutionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    // Unscoped lookup — for platform-level code and for resolving the
    // institution named in a token or invite. Tenant-facing code should use
    // findEntityByUserPublicId (the caller's current institution) instead.
    public Institution findEntityByPublicId(UUID publicId) {
        return institutionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + publicId));
    }

    /**
     * The institution the current request is acting in. Multi-tenant: this is
     * the institution named in the caller's access token (TenantContext), which
     * JwtAuthFilter has already confirmed they are a member of — or, for a
     * platform admin, explicitly entered. The userPublicId parameter must be the
     * caller; it is kept so the many existing call sites read unchanged.
     */
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
                .isActive(institution.isActive())
                .guardianLinkageMode(institution.getGuardianLinkageMode())
                .guardianNotificationDefault(institution.getGuardianNotificationDefault())
                .createdAt(institution.getCreatedAt())
                .build();
    }
}
