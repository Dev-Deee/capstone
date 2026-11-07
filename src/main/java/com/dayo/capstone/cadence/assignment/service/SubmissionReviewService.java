package com.dayo.capstone.cadence.assignment.service;

import com.dayo.capstone.cadence.assignment.dto.request.RedoExtensionRequest;
import com.dayo.capstone.cadence.assignment.dto.request.RegradeRequest;
import com.dayo.capstone.cadence.assignment.dto.request.ReviewSubmissionRequest;
import com.dayo.capstone.cadence.assignment.dto.response.AssignmentResponse;
import com.dayo.capstone.cadence.assignment.dto.response.ReviewRowResponse;
import com.dayo.capstone.cadence.assignment.enums.GradingType;
import com.dayo.capstone.cadence.assignment.enums.ReviewDecision;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.model.SubmissionAttempt;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionAttemptRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.GradesReleasedEvent;
import com.dayo.capstone.cadence.common.event.SubmissionSentBackEvent;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Facilitator review of submitted work: accept with a grade, or send back to
 * redo; change an accepted grade (with a reason, audited); give a student more
 * time or another attempt; and release grades so a cohort sees them at once.
 * Only the assignment's own facilitator or a SUPER_ADMIN may do any of it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionReviewService {

    static final int DEFAULT_REDO_DAYS = 3;
    private static final Set<SubmissionStatus> WAITING = EnumSet.of(SubmissionStatus.SUBMITTED, SubmissionStatus.LATE_SUBMITTED);

    private final SubmissionRepository submissionRepository;
    private final SubmissionAttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;
    private final AuditLogService auditLogService;
    private final DomainEvents domainEvents;

    private static final ObjectMapper JSON = new ObjectMapper();

    // Every enrolled student's work on the assignment, waiting work first (oldest first).
    @Transactional(readOnly = true)
    public List<ReviewRowResponse> listForAssignment(UUID assignmentPublicId, User caller, Institution institution) {
        Assignment assignment = assignmentService.findEntityByPublicIdScopedToInstitution(assignmentPublicId, institution);
        requireReviewer(assignment, caller);
        return submissionRepository.findByAssignment(assignment).stream()
                .sorted(Comparator.comparing((Submission s) -> WAITING.contains(s.getStatus()) ? 0 : 1)
                        .thenComparing(s -> s.getSubmittedAt() == null ? LocalDateTime.MAX : s.getSubmittedAt())
                        .thenComparing(s -> s.getStudent().getFullName()))
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public ReviewRowResponse review(UUID submissionPublicId, ReviewSubmissionRequest request, User caller, Institution institution) {
        Submission submission = findScoped(submissionPublicId, institution);
        Assignment assignment = submission.getAssignment();
        requireReviewer(assignment, caller);
        if (!WAITING.contains(submission.getStatus())) {
            throw new IllegalArgumentException("Only handed-in work waiting for review can be reviewed");
        }
        String feedback = blankToNull(request.getFeedback());
        LocalDateTime now = LocalDateTime.now();

        if (request.getDecision() == ReviewDecision.REDO) {
            if (feedback == null) {
                throw new IllegalArgumentException("Say what needs to be redone");
            }
            if (submission.getResubmissionCount() >= submission.attemptsAllowed()) {
                throw new IllegalArgumentException("No attempts left. Grant another attempt first, or accept the work.");
            }
            LocalDateTime due = request.getRedoDueAt() != null ? request.getRedoDueAt() : now.plusDays(DEFAULT_REDO_DAYS);
            if (!due.isAfter(now)) {
                throw new IllegalArgumentException("The redo date must be in the future");
            }
            submission.setScore(null);
            submission.setPassed(null);
            submission.setFeedback(feedback);
            submission.setRedoDueAt(due);
            submission.setStatus(SubmissionStatus.REDO_REQUESTED);
        } else {
            applyGrade(submission, assignment, request.getScore(), request.getPassed(), feedback);
            submission.setRedoDueAt(null);
            submission.setStatus(SubmissionStatus.REVIEWED);
        }
        submission.setReviewedBy(caller);
        submission.setReviewedAt(now);
        Submission saved = submissionRepository.save(submission);
        recordAttempt(saved, request.getDecision());

        Long institutionId = assignment.getCohort().getInstitution().getId();
        if (request.getDecision() == ReviewDecision.REDO) {
            domainEvents.publish(new SubmissionSentBackEvent(institutionId, caller.getId(),
                    saved.getStudent().getId(), assignment.getTitle(), saved.getRedoDueAt()));
        } else if (assignment.getGradesReleasedAt() != null) {
            // Grades are already out for this assignment: this student sees theirs now.
            domainEvents.publish(new GradesReleasedEvent(institutionId, caller.getId(),
                    List.of(saved.getStudent().getId()), assignment.getTitle()));
        }
        log.info("Submission reviewed: publicId={}, decision={}, reviewerPublicId={}",
                saved.getPublicId(), request.getDecision(), caller.getPublicId());
        return toRow(saved);
    }

    // Change an accepted grade. The old and new grade and the reason are audited.
    @Transactional
    public ReviewRowResponse regrade(UUID submissionPublicId, RegradeRequest request, User caller, Institution institution) {
        Submission submission = findScoped(submissionPublicId, institution);
        Assignment assignment = submission.getAssignment();
        requireReviewer(assignment, caller);
        if (submission.getStatus() != SubmissionStatus.REVIEWED) {
            throw new IllegalArgumentException("Only accepted work can be regraded");
        }
        String before = gradeJson(submission, null);
        String feedback = request.getFeedback() != null ? blankToNull(request.getFeedback()) : submission.getFeedback();
        applyGrade(submission, assignment, request.getScore(), request.getPassed(), feedback);
        Submission saved = submissionRepository.save(submission);
        auditLogService.log(caller.getPublicId(), primaryRole(caller), AuditAction.UPDATE, "SubmissionGrade",
                saved.getPublicId(), before, gradeJson(saved, request.getReason().trim()));
        log.info("Submission regraded: publicId={}, byPublicId={}", saved.getPublicId(), caller.getPublicId());
        return toRow(saved);
    }

    // More time to redo and/or one more attempt, at the facilitator's discretion.
    @Transactional
    public ReviewRowResponse extendRedo(UUID submissionPublicId, RedoExtensionRequest request, User caller, Institution institution) {
        Submission submission = findScoped(submissionPublicId, institution);
        requireReviewer(submission.getAssignment(), caller);
        if (request.getRedoDueAt() == null && !request.isGrantExtraAttempt()) {
            throw new IllegalArgumentException("Give a new redo date or another attempt");
        }
        String before = redoJson(submission);
        if (request.getRedoDueAt() != null) {
            if (submission.getStatus() != SubmissionStatus.REDO_REQUESTED) {
                throw new IllegalArgumentException("Only work sent back for a redo has a redo date");
            }
            if (!request.getRedoDueAt().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("The redo date must be in the future");
            }
            submission.setRedoDueAt(request.getRedoDueAt());
        }
        if (request.isGrantExtraAttempt()) {
            submission.setExtraAttempts(submission.getExtraAttempts() + 1);
        }
        Submission saved = submissionRepository.save(submission);
        auditLogService.log(caller.getPublicId(), primaryRole(caller), AuditAction.UPDATE, "SubmissionRedo",
                saved.getPublicId(), before, redoJson(saved));
        return toRow(saved);
    }

    // Grades become visible to every student whose work has been accepted.
    @Transactional
    public AssignmentResponse releaseGrades(UUID assignmentPublicId, User caller, Institution institution) {
        Assignment assignment = assignmentService.findEntityByPublicIdScopedToInstitution(assignmentPublicId, institution);
        requireReviewer(assignment, caller);
        if (assignment.getGradesReleasedAt() != null) {
            throw new IllegalArgumentException("Grades for this assignment are already released");
        }
        List<Long> students = submissionRepository.findByAssignment(assignment).stream()
                .filter(s -> s.getStatus() == SubmissionStatus.REVIEWED)
                .map(s -> s.getStudent().getId())
                .toList();
        if (students.isEmpty()) {
            throw new IllegalArgumentException("Accept at least one piece of work before releasing grades");
        }
        assignment.setGradesReleasedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        auditLogService.log(caller.getPublicId(), primaryRole(caller), AuditAction.UPDATE, "AssignmentGrades",
                assignment.getPublicId(), null, "{\"released\":true,\"students\":" + students.size() + "}");
        domainEvents.publish(new GradesReleasedEvent(assignment.getCohort().getInstitution().getId(),
                caller.getId(), students, assignment.getTitle()));
        log.info("Grades released: assignmentPublicId={}, students={}", assignment.getPublicId(), students.size());
        return assignmentService.getOne(assignmentPublicId, institution);
    }

    // Handed-in work waiting for this facilitator's review, across their assignments.
    @Transactional(readOnly = true)
    public long waitingCount(User facilitator) {
        return submissionRepository.countByAssignment_FacilitatorAndStatusIn(facilitator, WAITING);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void applyGrade(Submission submission, Assignment assignment, BigDecimal score, Boolean passed, String feedback) {
        GradingType type = assignment.getGradingType();
        switch (type) {
            case MARKS, PERCENTAGE -> {
                BigDecimal max = type == GradingType.MARKS
                        ? BigDecimal.valueOf(assignment.getMaxMarks() != null ? assignment.getMaxMarks() : 100)
                        : BigDecimal.valueOf(100);
                if (score == null) {
                    throw new IllegalArgumentException(type == GradingType.MARKS ? "Enter a mark out of " + max : "Enter a percentage");
                }
                if (score.signum() < 0 || score.compareTo(max) > 0) {
                    throw new IllegalArgumentException("The grade must be between 0 and " + max);
                }
                submission.setScore(score);
                submission.setPassed(null);
            }
            case PASS_FAIL -> {
                if (passed == null) throw new IllegalArgumentException("Choose pass or fail");
                submission.setPassed(passed);
                submission.setScore(null);
            }
            case FEEDBACK_ONLY -> {
                if (feedback == null) throw new IllegalArgumentException("Write your feedback");
                submission.setScore(null);
                submission.setPassed(null);
            }
        }
        submission.setFeedback(feedback);
    }

    private void recordAttempt(Submission submission, ReviewDecision decision) {
        SubmissionAttempt attempt = new SubmissionAttempt();
        attempt.setSubmission(submission);
        attempt.setAttemptNumber(attemptRepository.countBySubmission(submission) + 1);
        attempt.setSubmittedAt(submission.getSubmittedAt());
        attempt.setSubmissionType(submission.getSubmissionType());
        attempt.setLinkUrl(submission.getLinkUrl());
        attempt.setFileUrl(submission.getFileUrl());
        attempt.setDecision(decision);
        attempt.setScore(submission.getScore());
        attempt.setPassed(submission.getPassed());
        attempt.setFeedback(submission.getFeedback());
        attempt.setReviewedBy(submission.getReviewedBy());
        attempt.setReviewedAt(submission.getReviewedAt());
        attemptRepository.save(attempt);
    }

    private Submission findScoped(UUID publicId, Institution institution) {
        Submission submission = submissionService.findEntityByPublicId(publicId);
        if (!submission.getAssignment().getCohort().getInstitution().getId().equals(institution.getId())) {
            throw new ResourceNotFoundException("Submission not found: " + publicId);
        }
        return submission;
    }

    private static void requireReviewer(Assignment assignment, User caller) {
        if (!caller.hasRole(Role.SUPER_ADMIN) && !assignment.getFacilitator().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Only the assignment's own facilitator or a SUPER_ADMIN may review it");
        }
    }

    private static String primaryRole(User caller) {
        return caller.hasRole(Role.SUPER_ADMIN) ? "SUPER_ADMIN" : "FACILITATOR";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String gradeJson(Submission s, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", s.getScore());
        m.put("passed", s.getPassed());
        m.put("feedback", s.getFeedback());
        if (reason != null) m.put("reason", reason);
        return json(m);
    }

    private String redoJson(Submission s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("redoDueAt", s.getRedoDueAt() != null ? s.getRedoDueAt().toString() : null);
        m.put("extraAttempts", s.getExtraAttempts());
        return json(m);
    }

    private String json(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (Exception e) {
            return m.toString();
        }
    }

    private ReviewRowResponse toRow(Submission s) {
        Assignment a = s.getAssignment();
        LocalDateTime effectiveDeadline = submissionService.resolveEffectiveDeadline(s, a);
        List<ReviewRowResponse.Attempt> attempts = attemptRepository.findBySubmissionOrderByAttemptNumberAsc(s).stream()
                .map(at -> ReviewRowResponse.Attempt.builder()
                        .attemptNumber(at.getAttemptNumber())
                        .submittedAt(at.getSubmittedAt())
                        .submissionType(at.getSubmissionType())
                        .linkUrl(at.getLinkUrl())
                        .fileUrl(at.getFileUrl())
                        .decision(at.getDecision())
                        .score(at.getScore())
                        .passed(at.getPassed())
                        .feedback(at.getFeedback())
                        .reviewedByName(at.getReviewedBy().getFullName())
                        .reviewedAt(at.getReviewedAt())
                        .build())
                .toList();
        return ReviewRowResponse.builder()
                .publicId(s.getPublicId())
                .studentPublicId(s.getStudent().getPublicId())
                .studentName(s.getStudent().getFullName())
                .studentEmail(s.getStudent().getEmail())
                .status(s.getStatus())
                .submissionType(s.getSubmissionType())
                .linkUrl(s.getLinkUrl())
                .fileUrl(s.getFileUrl())
                .submittedAt(s.getSubmittedAt())
                .late(s.getStatus() == SubmissionStatus.LATE_SUBMITTED)
                .effectiveDeadline(effectiveDeadline)
                .resubmissionCount(s.getResubmissionCount())
                .attemptsAllowed(s.attemptsAllowed())
                .score(s.getScore())
                .passed(s.getPassed())
                .feedback(s.getFeedback())
                .reviewedAt(s.getReviewedAt())
                .reviewedByName(s.getReviewedBy() != null ? s.getReviewedBy().getFullName() : null)
                .redoDueAt(s.getRedoDueAt())
                .attempts(attempts)
                .build();
    }
}
