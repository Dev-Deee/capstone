package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.ExtensionRequest;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtensionRequestRepository extends JpaRepository<ExtensionRequest, Long> {
    Optional<ExtensionRequest> findByPublicId(UUID publicId);
    // findFirst — takes the most-recently-created approved extension if multiple exist,
    // rather than throwing IncorrectResultSizeDataAccessException.
    Optional<ExtensionRequest> findFirstBySubmissionAndStatusOrderByCreatedAtDesc(
            Submission submission, ExtensionRequestStatus status);
    List<ExtensionRequest> findBySubmission(Submission submission);
    List<ExtensionRequest> findBySubmission_Assignment_FacilitatorAndStatus(
            User facilitator, ExtensionRequestStatus status);
    boolean existsBySubmission_AssignmentAndStatus(Assignment assignment, ExtensionRequestStatus status);
    boolean existsBySubmission_AssignmentAndStatusAndRequestedNewDeadlineAfter(
            Assignment assignment, ExtensionRequestStatus status, LocalDateTime after);
}
