package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.dto.request.CreateSubjectRequest;
import com.dayo.capstone.cadence.institution.dto.response.SubjectResponse;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request, Institution institution) {
        Subject subject = new Subject();
        subject.setInstitution(institution);
        subject.setName(request.getName());
        subject.setDescription(request.getDescription());

        Subject saved = subjectRepository.save(subject);
        log.info("Subject created: publicId={}, name={}", saved.getPublicId(), saved.getName());

        return toResponse(saved);
    }

    public List<SubjectResponse> getAll() {
        return subjectRepository.findAll().stream().map(this::toResponse).toList();
    }

    // Public — needed by FacilitatorCohortService for cross-module lookup,
    // same pattern as InstitutionService/CohortService.
    public Subject findEntityByPublicId(UUID publicId) {
        return subjectRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + publicId));
    }

    // Tenant-scoped variant — mirrors CohortService's pattern (Section 18.1).
    public Subject findEntityByPublicIdScopedToInstitution(UUID subjectPublicId, Institution callerInstitution) {
        Subject subject = findEntityByPublicId(subjectPublicId);
        if (!subject.getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Subject not found: " + subjectPublicId);
        }
        return subject;
    }

    private SubjectResponse toResponse(Subject subject) {
        return SubjectResponse.builder()
                .publicId(subject.getPublicId())
                .institutionPublicId(subject.getInstitution().getPublicId())
                .name(subject.getName())
                .description(subject.getDescription())
                .isActive(subject.isActive())
                .build();
    }
}