package com.dayo.capstone.cadence.config;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.ExtensionRequestRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Every 15 minutes:
 *  1. Marks PENDING submissions whose effective deadline has passed as MISSING.
 *  2. Auto-closes OPEN assignments whose base deadline has passed, PROVIDED
 *     no submission on them has a PENDING_APPROVAL extension request awaiting
 *     review, and no APPROVED extension's requestedNewDeadline is still in the
 *     future. This enforces deadline accountability without ever locking out
 *     a student who has (or is waiting on) a legitimate extension.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentDeadlineEnforcementJob {

    private final SubmissionRepository submissionRepository;
    private final ExtensionRequestRepository extensionRequestRepository;
    private final AssignmentRepository assignmentRepository;

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void enforceDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        List<Submission> pending = submissionRepository.findByStatus(SubmissionStatus.PENDING);

        int markedMissing = 0;
        for (Submission submission : pending) {
            LocalDateTime effectiveDeadline = extensionRequestRepository
                    .findFirstBySubmissionAndStatusOrderByCreatedAtDesc(submission, ExtensionRequestStatus.APPROVED)
                    .map(ext -> ext.getRequestedNewDeadline())
                    .orElse(submission.getAssignment().getDeadline());

            if (now.isAfter(effectiveDeadline)) {
                submission.setStatus(SubmissionStatus.MISSING);
                submissionRepository.save(submission);
                markedMissing++;
            }
        }

        log.info("AssignmentDeadlineEnforcementJob: marked {} submissions as MISSING", markedMissing);

        autoCloseEligibleAssignments(now);
    }

    private void autoCloseEligibleAssignments(LocalDateTime now) {
        List<Assignment> candidates = assignmentRepository
                .findByStatusAndDeadlineBefore(AssignmentStatus.OPEN, now);

        int closed = 0;
        for (Assignment assignment : candidates) {
            boolean hasPendingExtension = extensionRequestRepository
                    .existsBySubmission_AssignmentAndStatus(assignment, ExtensionRequestStatus.PENDING_APPROVAL);
            boolean hasFutureApprovedExtension = extensionRequestRepository
                    .existsBySubmission_AssignmentAndStatusAndRequestedNewDeadlineAfter(
                            assignment, ExtensionRequestStatus.APPROVED, now);

            if (!hasPendingExtension && !hasFutureApprovedExtension) {
                assignment.setStatus(AssignmentStatus.CLOSED);
                assignmentRepository.save(assignment);
                closed++;
            }
        }

        if (closed > 0) {
            log.info("AssignmentDeadlineEnforcementJob: auto-closed {} assignments past deadline", closed);
        }
    }
}