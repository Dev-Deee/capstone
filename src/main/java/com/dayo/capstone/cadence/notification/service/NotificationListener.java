package com.dayo.capstone.cadence.notification.service;

import com.dayo.capstone.cadence.common.event.AttendanceOverriddenEvent;
import com.dayo.capstone.cadence.common.event.DomainEvent;
import com.dayo.capstone.cadence.common.event.InviteAcceptedEvent;
import com.dayo.capstone.cadence.common.event.StudentCohortChangedEvent;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.service.NotificationService.Draft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * Domain events → in-app notifications. Each handler runs after the
 * producer's transaction commits (a rolled-back change emits nothing) and on
 * the event executor, so it can neither slow nor roll back the producer.
 * Failures are logged at ERROR here and go no further.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM");

    private final NotificationService notifications;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AttendanceOverriddenEvent e) {
        handle(e, () -> List.of(new Draft(
                e.studentId(), NotificationType.ATTENDANCE_OVERRIDDEN,
                "Attendance updated",
                "Your attendance for " + e.what() + " on " + e.date().format(DAY)
                        + " was set to " + label(e.status()) + ".",
                "/dashboard/student/learning?tab=attendance", null)));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(StudentCohortChangedEvent e) {
        handle(e, () -> List.of(new Draft(
                e.studentId(), NotificationType.COHORT_CHANGED,
                e.fromCohortName() == null ? "You've joined " + e.toCohortName() : "You've moved to " + e.toCohortName(),
                e.fromCohortName() == null
                        ? "You're now enrolled in " + e.toCohortName() + "."
                        : "You've been moved from " + e.fromCohortName() + " to " + e.toCohortName()
                          + ". Your attendance and work in " + e.fromCohortName() + " are kept.",
                "/dashboard/student", null)));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(InviteAcceptedEvent e) {
        if (e.inviterId() == null) return;
        handle(e, () -> List.of(new Draft(
                e.inviterId(), NotificationType.INVITE_ACCEPTED,
                e.memberName() + " joined",
                e.memberName() + " accepted your invite and joined as " + roleLabel(e.role()) + ".",
                notifications.personRoute(e.inviterId(), e.institutionId(), e.memberPublicId()), e.memberPublicId())));
    }

    private void handle(DomainEvent event, Supplier<List<Draft>> drafts) {
        try {
            notifications.deliver(event, drafts.get());
        } catch (Exception ex) {
            log.error("Notification listener failed: event={}, eventId={}, institutionId={}",
                    event.getClass().getSimpleName(), event.eventId(), event.institutionId(), ex);
        }
    }

    private static String label(String status) {
        return switch (status) {
            case "PRESENT" -> "Present";
            case "LATE" -> "Late";
            case "ABSENT" -> "Absent";
            default -> status;
        };
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "STUDENT" -> "a student";
            case "FACILITATOR" -> "a facilitator";
            case "ADMIN" -> "an admin";
            case "SUPER_ADMIN" -> "a super admin";
            default -> role.toLowerCase();
        };
    }
}
