package com.dayo.capstone.cadence.assignment.service;

import com.dayo.capstone.cadence.assignment.dto.request.CreateAssignmentRequest;
import com.dayo.capstone.cadence.assignment.dto.response.AssignmentResponse;
import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.service.SubjectService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
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
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CohortService cohortService;
    private final SubjectService subjectService;
    private final FacilitatorCohortRepository facilitatorCohortRepository;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public AssignmentResponse create(CreateAssignmentRequest request, User facilitator, Institution callerInstitution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getCohortPublicId()), callerInstitution);
        Subject subject = subjectService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getSubjectPublicId()), callerInstitution);

        // Same validation as SessionService.open() — the facilitator must be
        // assigned to this exact cohort+subject pair before creating an assignment.
        facilitatorCohortRepository.findByFacilitatorAndCohortAndSubject(facilitator, cohort, subject)
                .filter(FacilitatorCohort::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not assigned to teach this subject in this cohort"));

        Assignment assignment = new Assignment();
        assignment.setCohort(cohort);
        assignment.setSubject(subject);
        assignment.setFacilitator(facilitator);
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setDeadline(request.getDeadline());
        if (request.getMaxResubmissions() != null) {
            assignment.setMaxResubmissions(request.getMaxResubmissions());
        }

        Assignment saved = assignmentRepository.save(assignment);
        log.info("Assignment created: publicId={}, cohortPublicId={}, subjectPublicId={}, facilitatorPublicId={}",
                saved.getPublicId(), cohort.getPublicId(), subject.getPublicId(), facilitator.getPublicId());

        return toResponse(saved);
    }

    // publish() goes directly to OPEN, auto-creating PENDING Submission rows for
    // every actively-enrolled student in one atomic transaction. PUBLISHED exists
    // in the status enum as a near-instantaneous intermediate — in practice we
    // skip straight to OPEN so the stored state is always the actionable one.
    @Transactional
    public AssignmentResponse publish(UUID publicId, User caller, Institution callerInstitution) {
        Assignment assignment = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);

        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new IllegalArgumentException("Only DRAFT assignments can be published");
        }
        if (!caller.hasRole(Role.SUPER_ADMIN) && !assignment.getFacilitator().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Only the assignment's own facilitator or a SUPER_ADMIN may publish it");
        }

        List<CohortEnrollment> enrollments = cohortEnrollmentRepository
                .findByCohortAndIsActiveTrue(assignment.getCohort());

        for (CohortEnrollment enrollment : enrollments) {
            Submission submission = new Submission();
            submission.setAssignment(assignment);
            submission.setStudent(enrollment.getStudent());
            submissionRepository.save(submission);
        }

        assignment.setStatus(AssignmentStatus.OPEN);
        Assignment saved = assignmentRepository.save(assignment);
        log.info("Assignment published: publicId={}, submissionsCreated={}", saved.getPublicId(), enrollments.size());

        return toResponse(saved);
    }

    @Transactional
    public AssignmentResponse close(UUID publicId, User caller, Institution callerInstitution) {
        Assignment assignment = findEntityByPublicIdScopedToInstitution(publicId, callerInstitution);

        if (assignment.getStatus() != AssignmentStatus.OPEN) {
            throw new IllegalArgumentException("Only OPEN assignments can be closed");
        }
        if (!caller.hasRole(Role.SUPER_ADMIN) && !assignment.getFacilitator().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Only the assignment's own facilitator or a SUPER_ADMIN may close it");
        }

        assignment.setStatus(AssignmentStatus.CLOSED);
        Assignment saved = assignmentRepository.save(assignment);
        log.info("Assignment closed: publicId={}, closedByPublicId={}", saved.getPublicId(), caller.getPublicId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getForCohort(UUID cohortPublicId, Institution callerInstitution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, callerInstitution);
        return assignmentRepository.findByCohort(cohort).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getForFacilitator(User facilitator) {
        return assignmentRepository.findByFacilitator(facilitator).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getOne(UUID publicId, Institution callerInstitution) {
        return toResponse(findEntityByPublicIdScopedToInstitution(publicId, callerInstitution));
    }

    // Public — used by SubmissionService and ExtensionRequestService for
    // cross-service lookups within the same institution scope.
    @Transactional(readOnly = true)
    public Assignment findEntityByPublicIdScopedToInstitution(UUID publicId, Institution callerInstitution) {
        Assignment assignment = findEntityByPublicId(publicId);
        if (!assignment.getCohort().getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Assignment not found: " + publicId);
        }
        return assignment;
    }

    public Assignment findEntityByPublicId(UUID publicId) {
        return assignmentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + publicId));
    }

    private AssignmentResponse toResponse(Assignment assignment) {
        return AssignmentResponse.builder()
                .publicId(assignment.getPublicId())
                .cohortPublicId(assignment.getCohort().getPublicId())
                .subjectPublicId(assignment.getSubject().getPublicId())
                .facilitatorPublicId(assignment.getFacilitator().getPublicId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .status(assignment.getStatus())
                .deadline(assignment.getDeadline())
                .maxResubmissions(assignment.getMaxResubmissions())
                .isLocked(assignment.isLocked())
                .clarificationNote(assignment.getClarificationNote())
                .createdAt(assignment.getCreatedAt())
                .build();
    }
}
