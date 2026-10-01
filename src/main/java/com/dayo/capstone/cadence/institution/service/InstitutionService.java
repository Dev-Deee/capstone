package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.dto.request.CreateInstitutionRequest;
import com.dayo.capstone.cadence.institution.dto.response.InstitutionResponse;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;

    @Transactional
    public InstitutionResponse create(CreateInstitutionRequest request) {
        Institution institution = new Institution();
        institution.setName(request.getName());
        institution.setAddress(request.getAddress());

        Institution saved = institutionRepository.save(institution);
        log.info("Institution created: publicId={}", saved.getPublicId());

        return toResponse(saved);
    }

    // Every lookup from outside this service uses publicId — never the
    // internal Long id, which stays entirely inside the repository/JPA layer.
    public InstitutionResponse getByPublicId(UUID publicId) {
        Institution institution = findEntityByPublicId(publicId);
        return toResponse(institution);
    }

    public List<InstitutionResponse> getAll() {
        return institutionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Package-private lookup returning the entity itself, for other
     * services in this module that need the JPA entity (e.g. to set a
     * FK relationship) rather than the external-facing DTO.
     */
    public Institution findEntityByPublicId(UUID publicId) {
        return institutionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Institution not found: " + publicId));
    }

    // InstitutionService.java — add this method
// InstitutionService.java
    @Transactional(readOnly = true)
    public Institution findEntityByUserPublicId(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));

        // user.getInstitution().getId() is safe — the ID is always known to
        // a lazy proxy upfront, reading it never triggers a DB hit. What we're
        // avoiding is calling any OTHER method on that proxy (like getPublicId()),
        // which would try to initialize it and fail outside this session.
        Long institutionId = user.getInstitution().getId();

        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found"));
    }

    private InstitutionResponse toResponse(Institution institution) {
        return InstitutionResponse.builder()
                .publicId(institution.getPublicId())
                .name(institution.getName())
                .address(institution.getAddress())
                .isActive(institution.isActive())
                .guardianLinkageMode(institution.getGuardianLinkageMode())
                .guardianNotificationDefault(institution.getGuardianNotificationDefault())
                .createdAt(institution.getCreatedAt())
                .build();
    }
}