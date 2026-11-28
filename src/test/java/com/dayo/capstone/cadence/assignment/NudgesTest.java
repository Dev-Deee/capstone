package com.dayo.capstone.cadence.assignment;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.NudgeKind;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.ExtensionRequest;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.NudgeLogRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.config.AssignmentNudgeJob;
import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automatic nudges: due-soon and overdue reminders to students who haven't
 * handed in, redo reminders, and the facilitators' morning review digest —
 * each sent at most once. The job runs with its own clock, a month ahead, so
 * other tests' fixtures never fall inside its windows.
 */
class NudgesTest extends IntegrationTestBase {

    @Autowired AssignmentNudgeJob job;
    @Autowired NudgeLogRepository nudgeLogRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired TransactionTemplate tx;
    @Autowired @Qualifier("eventExecutor") Executor eventExecutor;

    final LocalDateTime now = LocalDateTime.now().plusDays(30);

    Institution inst;
    Cohort cohort;
    Subject subject;
    User facilitator, pending, handedIn, leftCohort;

    @BeforeEach
    void setUp() {
        inst = institution("Nudge Academy");
        cohort = cohort(inst);
        subject = subject(inst);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        pending = student("pending", cohort, true);
        handedIn = student("handed", cohort, true);
        leftCohort = student("left", cohort, false);
    }

    private User student(String label, Cohort c, boolean active) {
        User u = user(label);
        member(u, c.getInstitution(), Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(u);
        e.setCohort(c);
        e.setEnrolledAt(LocalDateTime.now());
        e.setActive(active);
        enrollmentRepository.save(e);
        return u;
    }

    private Assignment assignment(Cohort c, LocalDateTime deadline, AssignmentStatus status) {
        Assignment a = new Assignment();
        a.setCohort(c);
        a.setSubject(subject);
        a.setFacilitator(facilitator);
        a.setTitle(unique("essay"));
        a.setDeadline(deadline);
        a.setStatus(status);
        return assignmentRepository.save(a);
    }

    private Submission work(Assignment a, User who, SubmissionStatus status) {
        Submission s = new Submission();
        s.setAssignment(a);
        s.setStudent(who);
        s.setStatus(status);
        if (status != SubmissionStatus.PENDING && status != SubmissionStatus.MISSING) {
            s.setSubmissionType(SubmissionType.LINK);
            s.setLinkUrl("https://example.com/" + unique("work"));
            s.setSubmittedAt(now.minusHours(1));
        }
        return submissionRepository.save(s);
    }

    private void extension(Submission s, ExtensionRequestStatus status, LocalDateTime until) {
        ExtensionRequest e = new ExtensionRequest();
        e.setSubmission(s);
        e.setStudent(s.getStudent());
        e.setRequestedNewDeadline(until);
        e.setReason("test");
        e.setStatus(status);
        extensionRequestRepository.save(e);
    }

    private List<Notification> notificationsOf(User u, NotificationType type) {
        return tx.execute(st -> notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(u.getId()) && n.getType() == type)
                .toList());
    }

    // Waits until every notification the job's events produce has been written.
    private void settle() {
        var pool = ((ThreadPoolTaskExecutor) eventExecutor).getThreadPoolExecutor();
        await(() -> pool.getQueue().isEmpty() && pool.getActiveCount() == 0
                && pool.getCompletedTaskCount() == pool.getTaskCount());
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("Timed out waiting");
    }

    @Test
    void dueSoonGoesOnceToActiveStudentsWhoHaventHandedIn() {
        Assignment due = assignment(cohort, now.plusHours(12), AssignmentStatus.OPEN);
        work(due, pending, SubmissionStatus.PENDING);
        work(due, handedIn, SubmissionStatus.SUBMITTED);
        work(due, leftCohort, SubmissionStatus.PENDING);
        // Not nudged: a draft, an assignment due later, a student with an approved extension.
        work(assignment(cohort, now.plusHours(12), AssignmentStatus.DRAFT), pending, SubmissionStatus.PENDING);
        work(assignment(cohort, now.plusDays(3), AssignmentStatus.OPEN), pending, SubmissionStatus.PENDING);
        User extended = student("ext", cohort, true);
        extension(work(due, extended, SubmissionStatus.PENDING), ExtensionRequestStatus.APPROVED, now.plusDays(4));

        job.sendDeadlineNudges(now);
        job.sendDeadlineNudges(now.plusMinutes(5));
        settle();

        List<Notification> got = notificationsOf(pending, NotificationType.ASSIGNMENT_DUE_SOON);
        assertThat(got).hasSize(1);
        assertThat(got.get(0).getLinkPublicId()).isEqualTo(due.getPublicId());
        assertThat(got.get(0).getTitle()).contains(due.getTitle());
        assertThat(got.get(0).getLinkRoute()).isEqualTo("/dashboard/student/learning?tab=assignments&show=due");
        assertThat(notificationsOf(handedIn, NotificationType.ASSIGNMENT_DUE_SOON)).isEmpty();
        assertThat(notificationsOf(leftCohort, NotificationType.ASSIGNMENT_DUE_SOON)).isEmpty();
        assertThat(notificationsOf(extended, NotificationType.ASSIGNMENT_DUE_SOON)).isEmpty();
        assertThat(nudgeLogRepository.countByKindAndTargetKey(NudgeKind.ASSIGNMENT_DUE_SOON, due.getPublicId().toString()))
                .isEqualTo(1);
    }

    @Test
    void suspendedInstitutionsAreNotNudged() {
        Institution suspended = institution("Suspended Academy");
        Cohort c = cohort(suspended);
        User stu = student("susp", c, true);
        work(assignment(c, now.plusHours(6), AssignmentStatus.OPEN), stu, SubmissionStatus.PENDING);
        suspended.setStatus(InstitutionStatus.SUSPENDED);
        institutionRepository.save(suspended);

        job.sendDeadlineNudges(now);
        settle();

        assertThat(notificationsOf(stu, NotificationType.ASSIGNMENT_DUE_SOON)).isEmpty();
    }

    @Test
    void redoReminderGoesOncePerRoundBeforeTheRedoByDate() {
        Assignment a = assignment(cohort, now.minusDays(2), AssignmentStatus.CLOSED);
        Submission soon = work(a, pending, SubmissionStatus.REDO_REQUESTED);
        soon.setRedoDueAt(now.plusHours(6));
        submissionRepository.save(soon);
        Submission later = work(a, handedIn, SubmissionStatus.REDO_REQUESTED);
        later.setRedoDueAt(now.plusDays(3));
        submissionRepository.save(later);

        job.sendDeadlineNudges(now);
        job.sendDeadlineNudges(now.plusMinutes(5));
        settle();

        List<Notification> got = notificationsOf(pending, NotificationType.REDO_DUE_SOON);
        assertThat(got).hasSize(1);
        assertThat(got.get(0).getLinkRoute()).endsWith("show=redo");
        assertThat(notificationsOf(handedIn, NotificationType.REDO_DUE_SOON)).isEmpty();

        // Handed in again and sent back once more: the new round gets its own reminder.
        soon.setResubmissionCount(1);
        soon.setRedoDueAt(now.plusHours(20));
        submissionRepository.save(soon);
        job.sendDeadlineNudges(now.plusMinutes(10));
        settle();
        assertThat(notificationsOf(pending, NotificationType.REDO_DUE_SOON)).hasSize(2);
    }

    @Test
    void overdueGoesOnlyWhileTheAssignmentStillTakesWork() {
        // Stays open: another student's extension request is waiting for review.
        Assignment open = assignment(cohort, now.minusHours(2), AssignmentStatus.OPEN);
        work(open, pending, SubmissionStatus.MISSING);
        work(open, handedIn, SubmissionStatus.SUBMITTED);
        User asked = student("asked", cohort, true);
        extension(work(open, asked, SubmissionStatus.PENDING), ExtensionRequestStatus.PENDING_APPROVAL, now.plusDays(2));
        // About to be auto-closed (no extensions) and already closed: no reminder.
        Assignment closing = assignment(cohort, now.minusHours(2), AssignmentStatus.OPEN);
        work(closing, pending, SubmissionStatus.PENDING);
        Assignment closed = assignment(cohort, now.minusHours(2), AssignmentStatus.CLOSED);
        work(closed, pending, SubmissionStatus.MISSING);
        // Not yet past overdue-after.
        Assignment justPassed = assignment(cohort, now.minusMinutes(5), AssignmentStatus.OPEN);
        work(justPassed, pending, SubmissionStatus.PENDING);
        extension(work(justPassed, asked, SubmissionStatus.PENDING), ExtensionRequestStatus.PENDING_APPROVAL, now.plusDays(2));

        job.sendDeadlineNudges(now);
        job.sendDeadlineNudges(now.plusMinutes(5));
        settle();

        List<Notification> got = notificationsOf(pending, NotificationType.ASSIGNMENT_OVERDUE);
        assertThat(got).hasSize(1);
        assertThat(got.get(0).getLinkPublicId()).isEqualTo(open.getPublicId());
        assertThat(got.get(0).getBody()).contains("extension");
        assertThat(notificationsOf(handedIn, NotificationType.ASSIGNMENT_OVERDUE)).isEmpty();
        assertThat(notificationsOf(asked, NotificationType.ASSIGNMENT_OVERDUE)).isEmpty();
    }

    @Test
    void theMorningDigestCountsWaitingWorkOncePerDay() {
        User s3 = student("s3", cohort, true);
        Assignment a1 = assignment(cohort, now.minusDays(1), AssignmentStatus.CLOSED);
        Assignment a2 = assignment(cohort, now.plusDays(1), AssignmentStatus.OPEN);
        work(a1, pending, SubmissionStatus.SUBMITTED);
        work(a1, handedIn, SubmissionStatus.LATE_SUBMITTED);
        work(a2, pending, SubmissionStatus.SUBMITTED);
        // Not waiting: reviewed, sent back, not handed in.
        work(a2, handedIn, SubmissionStatus.REVIEWED);
        work(a1, s3, SubmissionStatus.REDO_REQUESTED);
        work(a2, s3, SubmissionStatus.PENDING);
        User idle = user("idle");
        member(idle, inst, Role.FACILITATOR);

        LocalDate day = now.toLocalDate();
        job.sendReviewDigest(day);
        job.sendReviewDigest(day);
        settle();

        List<Notification> got = notificationsOf(facilitator, NotificationType.REVIEW_WAITING_DIGEST);
        assertThat(got).hasSize(1);
        assertThat(got.get(0).getTitle()).isEqualTo("3 submissions waiting for review");
        assertThat(got.get(0).getBody()).contains("across 2 assignments");
        assertThat(got.get(0).getLinkRoute()).isEqualTo("/dashboard/facilitator");
        assertThat(notificationsOf(idle, NotificationType.REVIEW_WAITING_DIGEST)).isEmpty();

        job.sendReviewDigest(day.plusDays(1));
        settle();
        assertThat(notificationsOf(facilitator, NotificationType.REVIEW_WAITING_DIGEST)).hasSize(2);
    }
}
