package com.dayo.capstone.cadence.assignment.service;

import com.dayo.capstone.cadence.common.tenant.TenantGuard;
import com.dayo.capstone.cadence.assignment.dto.request.CreateExtensionRequestRequest;
import com.dayo.capstone.cadence.assignment.dto.request.ReviewExtensionRequest;
import com.dayo.capstone.cadence.assignment.dto.response.ExtensionRequestResponse;
import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.model.ExtensionRequest;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.ExtensionRequestRepository;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
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
public class ExtensionRequestService {

    private final ExtensionRequestRepository extensionRequestRepository;
    private final SubmissionService submissionService;

    @Transactional
    public ExtensionRequestResponse requestExtension(UUID submissionPublicId, User student,
                                                     CreateExtensionRequestRequest request) {
        Submission submission = submissionService.findEntityByPublicId(submissionPublicId);

        if (!submission.getStudent().getId().equals(student.getId())) {
            throw new AccessDeniedException("You are not authorised to request an extension for this submission");
        }

        if (submission.getAssignment().getStatus() == AssignmentStatus.CLOSED) {
            throw new IllegalArgumentException("Cannot request an extension for a closed assignment");
        }

        ExtensionRequest extensionRequest = new ExtensionRequest();
        extensionRequest.setSubmission(submission);
        extensionRequest.setStudent(student);
        extensionRequest.setRequestedNewDeadline(request.getRequestedNewDeadline());
        extensionRequest.setReason(request.getReason());

        ExtensionRequest saved = extensionRequestRepository.save(extensionRequest);
        log.info("Extension request created: publicId={}, submissionPublicId={}, studentPublicId={}",
                saved.getPublicId(), submission.getPublicId(), student.getPublicId());

        return toResponse(saved);
    }

    @Transactional
    public ExtensionRequestResponse reviewExtension(UUID extensionRequestPublicId, User reviewer,
                                                    ReviewExtensionRequest request) {
        ExtensionRequest extensionRequest = findEntityByPublicId(extensionRequestPublicId);
        // The SUPER_ADMIN short-circuit below must only apply inside their own institution.
        TenantGuard.requireCurrent(extensionRequest.getSubmission().getAssignment().getCohort().getInstitution().getId(),
                "Extension request");
        User assignmentFacilitator = extensionRequest.getSubmission().getAssignment().getFacilitator();

        // Only the assignment's own facilitator or a SUPER_ADMIN may review.
        if (!reviewer.hasRole(Role.SUPER_ADMIN) && !assignmentFacilitator.getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("Only the assignment's facilitator or a SUPER_ADMIN may review extension requests");
        }

        if (extensionRequest.getStatus() != ExtensionRequestStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("This extension request has already been reviewed");
        }

        extensionRequest.setStatus(request.getApprove()
                ? ExtensionRequestStatus.APPROVED
                : ExtensionRequestStatus.DENIED);
        extensionRequest.setReviewedBy(reviewer);
        extensionRequest.setReviewNote(request.getReviewNote());

        ExtensionRequest saved = extensionRequestRepository.save(extensionRequest);
        log.info("Extension request reviewed: publicId={}, decision={}, reviewerPublicId={}",
                saved.getPublicId(), saved.getStatus(), reviewer.getPublicId());

        return toResponse(saved);
    }

    public ExtensionRequest findEntityByPublicId(UUID publicId) {
        return extensionRequestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Extension request not found: " + publicId));
    }

    @Transactional(readOnly = true)
    public List<ExtensionRequestResponse> getPendingForFacilitator(User facilitator) {
        return extensionRequestRepository
                .findBySubmission_Assignment_FacilitatorAndStatus(facilitator, ExtensionRequestStatus.PENDING_APPROVAL)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ExtensionRequestResponse toResponse(ExtensionRequest ext) {
        return ExtensionRequestResponse.builder()
                .publicId(ext.getPublicId())
                .submissionPublicId(ext.getSubmission().getPublicId())
                .studentPublicId(ext.getStudent().getPublicId())
                .studentName(ext.getStudent().getFullName())
                .assignmentTitle(ext.getSubmission().getAssignment().getTitle())
                .assignmentPublicId(ext.getSubmission().getAssignment().getPublicId())
                .cohortPublicId(ext.getSubmission().getAssignment().getCohort().getPublicId())
                .requestedNewDeadline(ext.getRequestedNewDeadline())
                .reason(ext.getReason())
                .status(ext.getStatus())
                .reviewedByPublicId(ext.getReviewedBy() != null ? ext.getReviewedBy().getPublicId() : null)
                .reviewNote(ext.getReviewNote())
                .createdAt(ext.getCreatedAt())
                .build();
    }
}
