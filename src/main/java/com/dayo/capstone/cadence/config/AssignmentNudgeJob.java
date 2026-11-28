package com.dayo.capstone.cadence.config;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.NudgeKind;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.ExtensionRequestRepository;
import com.dayo.capstone.cadence.assignment.repository.NudgeLogRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository.WaitingForReview;
import com.dayo.capstone.cadence.common.event.AssignmentDueSoonEvent;
import com.dayo.capstone.cadence.common.event.AssignmentOverdueEvent;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.RedoDueSoonEvent;
import com.dayo.capstone.cadence.common.event.ReviewsWaitingDigestEvent;
import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automatic reminders, across every institution that isn't suspended:
 *  - every few minutes: assignments due soon and overdue (to students who
 *    haven't handed anything in), and redos due soon (to the student);
 *  - each morning: one summary per facilitator of work waiting for review.
 *
 * Each reminder goes to a person at most once: a NudgeLog row is claimed
 * first and the database's unique constraint turns away a second claim, so
 * reruns, restarts and several instances never repeat one. Notifications go
 * out through domain events after the claim commits; the windows are
 * configured under app.nudges.* in application.properties.
 *
 * Overdue reminders go only while the assignment still takes work: it is
 * OPEN and AssignmentDeadlineEnforcementJob will keep it open (an extension
 * is pending, or an approved one runs later). Past the deadline a student
 * can't hand in without an extension, so the reminder tells them to ask for one.
 */
@Slf4j
@Component
public class AssignmentNudgeJob {

    private static final EnumSet<SubmissionStatus> WAITING = EnumSet.of(SubmissionStatus.SUBMITTED, SubmissionStatus.LATE_SUBMITTED);

    private final SubmissionRepository submissionRepository;
    private final ExtensionRequestRepository extensionRequestRepository;
    private final NudgeLogRepository nudgeLogRepository;
    private final DomainEvents domainEvents;
    private final Duration dueSoonLead;
    private final Duration overdueAfter;
    private final Duration overdueWindow;
    private final Duration redoLead;

    public AssignmentNudgeJob(SubmissionRepository submissionRepository,
                              ExtensionRequestRepository extensionRequestRepository,
                              NudgeLogRepository nudgeLogRepository, DomainEvents domainEvents,
                              @Value("${app.nudges.due-soon-lead:PT24H}") Duration dueSoonLead,
                              @Value("${app.nudges.overdue-after:PT30M}") Duration overdueAfter,
                              @Value("${app.nudges.overdue-window:PT24H}") Duration overdueWindow,
                              @Value("${app.nudges.redo-lead:PT24H}") Duration redoLead) {
        this.submissionRepository = submissionRepository;
        this.extensionRequestRepository = extensionRequestRepository;
        this.nudgeLogRepository = nudgeLogRepository;
        this.domainEvents = domainEvents;
        this.dueSoonLead = dueSoonLead;
        this.overdueAfter = overdueAfter;
        this.overdueWindow = overdueWindow;
        this.redoLead = redoLead;
    }

    @Scheduled(cron = "${app.nudges.cron:0 */5 * * * *}")
    @Transactional
    public void sendDeadlineNudges() {
        sendDeadlineNudges(LocalDateTime.now());
    }

    @Scheduled(cron = "${app.nudges.digest-cron:0 0 8 * * *}")
    @Transactional
    public void sendReviewDigest() {
        sendReviewDigest(LocalDate.now());
    }

    /** One pass of the deadline reminders as of {@code now} (tests pass their own clock). */
    @Transactional
    public void sendDeadlineNudges(LocalDateTime now) {
        int dueSoon = dueSoon(now);
        int overdue = overdue(now);
        int redo = redoDueSoon(now);
        if (dueSoon + overdue + redo > 0) {
            log.info("AssignmentNudgeJob: sent dueSoon={}, overdue={}, redoDueSoon={}", dueSoon, overdue, redo);
        }
    }

    /** The morning review summary for {@code day}; at most one per facilitator, institution and day. */
    @Transactional
    public void sendReviewDigest(LocalDate day) {
        int sent = 0;
        for (WaitingForReview w : submissionRepository.countWaitingForReview(WAITING, InstitutionStatus.SUSPENDED)) {
            if (!claim(NudgeKind.REVIEW_WAITING_DIGEST, w.getFacilitatorId(), day + ":" + w.getInstitutionPublicId())) continue;
            domainEvents.publish(new ReviewsWaitingDigestEvent(w.getInstitutionId(), w.getFacilitatorId(),
                    w.getSubmissions(), w.getAssignments()));
            sent++;
        }
        if (sent > 0) log.info("AssignmentNudgeJob: sent {} review digest(s) for {}", sent, day);
    }

    private int dueSoon(LocalDateTime now) {
        List<Submission> notHandedIn = submissionRepository.findNotHandedInDueBetween(
                EnumSet.of(SubmissionStatus.PENDING), AssignmentStatus.OPEN, now, now.plus(dueSoonLead),
                InstitutionStatus.SUSPENDED, EnumSet.of(ExtensionRequestStatus.APPROVED));
        int sent = 0;
        for (List<Submission> rows : byAssignment(notHandedIn).values()) {
            Assignment a = rows.get(0).getAssignment();
            List<Long> students = claimEach(NudgeKind.ASSIGNMENT_DUE_SOON, rows, a.getPublicId().toString());
            if (students.isEmpty()) continue;
            domainEvents.publish(new AssignmentDueSoonEvent(institutionId(a), students, a.getTitle(),
                    a.getPublicId(), a.getDeadline()));
            sent += students.size();
        }
        return sent;
    }

    private int overdue(LocalDateTime now) {
        // A student with an extension (asked for or granted) has their own deadline; they're left alone.
        List<Submission> notHandedIn = submissionRepository.findNotHandedInDueBetween(
                EnumSet.of(SubmissionStatus.PENDING, SubmissionStatus.MISSING), AssignmentStatus.OPEN,
                now.minus(overdueWindow), now.minus(overdueAfter), InstitutionStatus.SUSPENDED,
                EnumSet.of(ExtensionRequestStatus.APPROVED, ExtensionRequestStatus.PENDING_APPROVAL));
        int sent = 0;
        for (List<Submission> rows : byAssignment(notHandedIn).values()) {
            Assignment a = rows.get(0).getAssignment();
            if (!staysOpen(a, now)) continue;
            List<Long> students = claimEach(NudgeKind.ASSIGNMENT_OVERDUE, rows, a.getPublicId().toString());
            if (students.isEmpty()) continue;
            domainEvents.publish(new AssignmentOverdueEvent(institutionId(a), students, a.getTitle(),
                    a.getPublicId(), a.getDeadline()));
            sent += students.size();
        }
        return sent;
    }

    private int redoDueSoon(LocalDateTime now) {
        int sent = 0;
        for (Submission s : submissionRepository.findRedoDueBetween(SubmissionStatus.REDO_REQUESTED,
                now, now.plus(redoLead), InstitutionStatus.SUSPENDED)) {
            // Once per redo round: the count goes up each time the work is handed in again.
            String target = s.getPublicId() + "#" + s.getResubmissionCount();
            if (!claim(NudgeKind.REDO_DUE_SOON, s.getStudent().getId(), target)) continue;
            Assignment a = s.getAssignment();
            domainEvents.publish(new RedoDueSoonEvent(institutionId(a), s.getStudent().getId(), a.getTitle(),
                    a.getPublicId(), s.getRedoDueAt()));
            sent++;
        }
        return sent;
    }

    // The same test AssignmentDeadlineEnforcementJob uses before auto-closing past its deadline.
    private boolean staysOpen(Assignment a, LocalDateTime now) {
        return extensionRequestRepository.existsBySubmission_AssignmentAndStatus(a, ExtensionRequestStatus.PENDING_APPROVAL)
                || extensionRequestRepository.existsBySubmission_AssignmentAndStatusAndRequestedNewDeadlineAfter(
                        a, ExtensionRequestStatus.APPROVED, now);
    }

    private static Map<Long, List<Submission>> byAssignment(List<Submission> submissions) {
        Map<Long, List<Submission>> grouped = new LinkedHashMap<>();
        for (Submission s : submissions) {
            grouped.computeIfAbsent(s.getAssignment().getId(), k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    // The students this run claimed, so may notify; the rest were reminded before.
    private List<Long> claimEach(NudgeKind kind, List<Submission> rows, String target) {
        List<Long> claimed = new ArrayList<>();
        for (Submission s : rows) {
            Long studentId = s.getStudent().getId();
            if (claim(kind, studentId, target)) claimed.add(studentId);
        }
        return claimed;
    }

    private boolean claim(NudgeKind kind, Long recipientId, String target) {
        return nudgeLogRepository.claim(UUID.randomUUID(), LocalDateTime.now(), kind.name(), recipientId, target) == 1;
    }

    private static Long institutionId(Assignment a) {
        return a.getCohort().getInstitution().getId();
    }
}
