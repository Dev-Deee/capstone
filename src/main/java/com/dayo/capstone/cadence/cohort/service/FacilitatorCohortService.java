package com.dayo.capstone.cadence.cohort.service;

import com.dayo.capstone.cadence.cohort.dto.request.AssignFacilitatorRequest;
import com.dayo.capstone.cadence.cohort.dto.response.FacilitatorCohortResponse;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.service.SubjectService;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitatorCohortService {

    private final FacilitatorCohortRepository facilitatorCohortRepository;
    private final CohortService cohortService;
    private final SubjectService subjectService;
    private final UserService userService;

    @Transactional
    public FacilitatorCohortResponse assign(AssignFacilitatorRequest request, Institution callerInstitution) {
        // Every lookup scoped to the caller's own institution — a
        // Management user at Institution A cannot assign a facilitator
        // to a Cohort or Subject belonging to Institution B (Section 18.1).
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getCohortPublicId()), callerInstitution);
        Subject subject = subjectService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getSubjectPublicId()), callerInstitution);
        User facilitator = userService.findEntityByPublicId(UUID.fromString(request.getFacilitatorPublicId()));

        if (!facilitator.hasRole(Role.FACILITATOR)) {
            throw new IllegalArgumentException("Target user does not hold the FACILITATOR role");
        }

        facilitatorCohortRepository.findByFacilitatorAndCohortAndSubject(facilitator, cohort, subject)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "This facilitator is already assigned to this subject in this cohort");
                });

        FacilitatorCohort assignment = new FacilitatorCohort();
        assignment.setFacilitator(facilitator);
        assignment.setCohort(cohort);
        assignment.setSubject(subject);
        assignment.setAssignedAt(LocalDateTime.now());

        FacilitatorCohort saved = facilitatorCohortRepository.save(assignment);
        log.info("Facilitator assigned: facilitatorPublicId={}, cohortPublicId={}, subjectPublicId={}",
                facilitator.getPublicId(), cohort.getPublicId(), subject.getPublicId());

        return toResponse(saved);
    }

    public List<FacilitatorCohortResponse> getForFacilitator(UUID facilitatorPublicId) {
        User facilitator = userService.findEntityByPublicId(facilitatorPublicId);
        return facilitatorCohortRepository.findByFacilitatorAndIsActiveTrue(facilitator).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private FacilitatorCohortResponse toResponse(FacilitatorCohort fc) {
        return FacilitatorCohortResponse.builder()
                .publicId(fc.getPublicId())
                .facilitatorPublicId(fc.getFacilitator().getPublicId())
                .cohortPublicId(fc.getCohort().getPublicId())
                .subjectPublicId(fc.getSubject().getPublicId())
                .assignedAt(fc.getAssignedAt())
                .isActive(fc.isActive())
                .build();
    }
}