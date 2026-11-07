package com.dayo.capstone.cadence.assignment.service;

import com.dayo.capstone.cadence.assignment.dto.response.SubmissionResponse;
import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.ExtensionRequestRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.common.service.FileStorageService;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final ExtensionRequestRepository extensionRequestRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public SubmissionResponse submit(UUID submissionPublicId, User student,
                                     SubmissionType submissionType, String linkUrl,
                                     byte[] fileBytes, String originalFilename) {
        Submission submission = findEntityByPublicId(submissionPublicId);

        if (!submission.getStudent().getId().equals(student.getId())) {
            throw new AccessDeniedException("You are not authorised to submit this assignment");
        }

        Assignment assignment = submission.getAssignment();
        LocalDateTime now = LocalDateTime.now();

        // Work sent back for a redo can be handed in until its redo-by date,
        // even after the assignment's own deadline or closing.
        boolean redo = submission.getStatus() == SubmissionStatus.REDO_REQUESTED;
        if (submission.getStatus() == SubmissionStatus.REVIEWED) {
            throw new IllegalArgumentException("This work has already been reviewed");
        }
        if (redo) {
            if (submission.getRedoDueAt() != null && now.isAfter(submission.getRedoDueAt())) {
                throw new IllegalArgumentException("The time to redo this work has passed. Ask your facilitator for more time.");
            }
        } else {
            if (assignment.getStatus() != AssignmentStatus.OPEN) {
                throw new IllegalArgumentException("This assignment is not open for submissions");
            }
            if (now.isAfter(resolveEffectiveDeadline(submission, assignment))) {
                throw new IllegalArgumentException("The submission deadline has passed");
            }
        }

        // Resubmission guard — resubmissionCount is incremented on every submit
        // AFTER the first (submittedAt being non-null indicates a prior submission).
        // The facilitator may grant extra attempts beyond the assignment's limit.
        if (submission.getSubmittedAt() != null) {
            if (submission.getResubmissionCount() >= submission.attemptsAllowed()) {
                throw new IllegalArgumentException("Maximum number of resubmissions has been reached");
            }
            submission.setResubmissionCount(submission.getResubmissionCount() + 1);
        }

        // Handle file vs link
        if (submissionType == SubmissionType.FILE) {
            String fileUrl = fileStorageService.upload(fileBytes, originalFilename, "submissions");
            submission.setFileUrl(fileUrl);
            submission.setLinkUrl(null);
        } else {
            if (linkUrl == null || linkUrl.isBlank()) {
                throw new IllegalArgumentException("A link URL is required for LINK submissions");
            }
            submission.setLinkUrl(linkUrl);
            submission.setFileUrl(null);
        }

        submission.setSubmissionType(submissionType);
        submission.setSubmittedAt(now);

        // SUBMITTED if before the original assignment deadline; LATE_SUBMITTED
        // if after it but still within an approved extension window. A redo
        // handed in before its redo-by date is on time.
        SubmissionStatus newStatus = !redo && now.isAfter(assignment.getDeadline())
                ? SubmissionStatus.LATE_SUBMITTED
                : SubmissionStatus.SUBMITTED;
        submission.setStatus(newStatus);

        // First submission across the entire assignment — lock it so the
        // facilitator can no longer edit the core content.
        if (!assignment.isLocked()) {
            assignment.setLocked(true);
            assignmentRepository.save(assignment);
        }

        Submission saved = submissionRepository.save(submission);
        log.info("Submission saved: publicId={}, assignmentPublicId={}, studentPublicId={}, status={}",
                saved.getPublicId(), assignment.getPublicId(), student.getPublicId(), newStatus);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getMine(User student) {
        return submissionRepository.findByStudent(student).stream()
                .map(this::toResponse)
                .toList();
    }

    // Public — used by ExtensionRequestService.
    @Transactional(readOnly = true)
    public Submission findEntityByPublicId(UUID publicId) {
        return submissionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + publicId));
    }

    // Returns the effective deadline for a submission: the approved extension's
    // requestedNewDeadline if one exists, otherwise the assignment's base deadline.
    // Package-visible so the enforcement job can call the same logic via the service.
    LocalDateTime resolveEffectiveDeadline(Submission submission, Assignment assignment) {
        return extensionRequestRepository
                .findFirstBySubmissionAndStatusOrderByCreatedAtDesc(submission, ExtensionRequestStatus.APPROVED)
                .map(ext -> ext.getRequestedNewDeadline())
                .orElse(assignment.getDeadline());
    }

    private SubmissionResponse toResponse(Submission submission) {
        LocalDateTime effectiveDeadline = extensionRequestRepository
                .findFirstBySubmissionAndStatusOrderByCreatedAtDesc(submission, ExtensionRequestStatus.APPROVED)
                .map(e -> e.getRequestedNewDeadline())
                .orElse(submission.getAssignment().getDeadline());
        ExtensionRequestStatus extensionStatus = extensionRequestRepository.findBySubmission(submission).stream()
                .max(java.util.Comparator.comparing(e -> e.getCreatedAt()))
                .map(e -> e.getStatus())
                .orElse(null);
        Assignment assignment = submission.getAssignment();
        boolean released = assignment.getGradesReleasedAt() != null;
        // Until grades are released, accepted work reads as handed in and its
        // grade and comment stay hidden. A send-back always shows: the student must act.
        boolean hideReview = submission.getStatus() == SubmissionStatus.REVIEWED && !released;
        boolean showReview = submission.getStatus() == SubmissionStatus.REDO_REQUESTED
                || (submission.getStatus() == SubmissionStatus.REVIEWED && released);
        SubmissionStatus shownStatus = hideReview
                ? (submission.getSubmittedAt() != null && submission.getSubmittedAt().isAfter(assignment.getDeadline())
                    ? SubmissionStatus.LATE_SUBMITTED : SubmissionStatus.SUBMITTED)
                : submission.getStatus();
        return SubmissionResponse.builder()
                .publicId(submission.getPublicId())
                .assignmentPublicId(assignment.getPublicId())
                .studentPublicId(submission.getStudent().getPublicId())
                .status(shownStatus)
                .submissionType(submission.getSubmissionType())
                .linkUrl(submission.getLinkUrl())
                .fileUrl(submission.getFileUrl())
                .submittedAt(submission.getSubmittedAt())
                .resubmissionCount(submission.getResubmissionCount())
                .createdAt(submission.getCreatedAt())
                .effectiveDeadline(effectiveDeadline)
                .extensionStatus(extensionStatus)
                .attemptsAllowed(submission.attemptsAllowed())
                .gradingType(assignment.getGradingType())
                .maxMarks(assignment.getMaxMarks())
                .score(showReview ? submission.getScore() : null)
                .passed(showReview ? submission.getPassed() : null)
                .feedback(showReview ? submission.getFeedback() : null)
                .reviewedAt(showReview ? submission.getReviewedAt() : null)
                .redoDueAt(submission.getStatus() == SubmissionStatus.REDO_REQUESTED ? submission.getRedoDueAt() : null)
                .gradesReleased(released)
                .build();
    }
}
