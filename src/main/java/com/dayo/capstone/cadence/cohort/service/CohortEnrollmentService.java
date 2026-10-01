package com.dayo.capstone.cadence.cohort.service;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEnrollmentService {

    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AdminPermissionService adminPermissionService;
    private final AuditLogService auditLogService;

    // Single entry point for "put a NEW student in this cohort" — used by
    // self-registration (AuthService) and invite-code join (both of which
    // guarantee the student has no active enrollment). Moving a student who is
    // already in a cohort goes through assignByAdmin() instead.
    @Transactional
    public CohortEnrollment enroll(User student, Cohort cohort) {
        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setStudent(student);
        enrollment.setCohort(cohort);
        enrollment.setEnrolledAt(LocalDateTime.now());
        CohortEnrollment saved = cohortEnrollmentRepository.save(enrollment);
        backfillOpenAssignments(student, cohort);
        return saved;
    }

    // Admin/super-admin enroll-or-move. The app assumes at most one active
    // enrollment per student, so a move ends the current enrollment (history is
    // kept, PRD 18.3) and starts the new one from today — the late-joiner rule
    // then keeps earlier sessions/assignments out of the student's rates.
    // Students cannot do this themselves; joining by code refuses if already enrolled.
    @Transactional
    public CohortEnrollment assignByAdmin(User student, Cohort target, User caller) {
        if (!student.hasRole(Role.STUDENT)) {
            throw new IllegalArgumentException("Only student accounts can be enrolled in a cohort");
        }
        if (!student.getInstitution().getId().equals(target.getInstitution().getId())) {
            throw new IllegalArgumentException("Student and cohort belong to different institutions");
        }

        Optional<CohortEnrollment> current = cohortEnrollmentRepository.findByStudentAndIsActiveTrue(student);
        if (current.isPresent() && current.get().getCohort().getId().equals(target.getId())) {
            throw new IllegalArgumentException(student.getFullName() + " is already in " + target.getName());
        }

        // An admin needs MANAGE_ENROLLMENTS for the destination and, when moving,
        // for the cohort the student is leaving too.
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_ENROLLMENTS, target);
            current.ifPresent(e -> adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_ENROLLMENTS, e.getCohort()));
        }

        String fromCohort = current.map(e -> e.getCohort().getName()).orElse(null);
        current.ifPresent(e -> {
            e.setActive(false);
            cohortEnrollmentRepository.save(e);
        });

        // (student, cohort) is unique, so a student returning to an old cohort
        // reactivates that row rather than inserting a new one.
        CohortEnrollment enrollment = cohortEnrollmentRepository.findByStudentAndCohort(student, target)
                .orElseGet(() -> {
                    CohortEnrollment fresh = new CohortEnrollment();
                    fresh.setStudent(student);
                    fresh.setCohort(target);
                    return fresh;
                });
        enrollment.setActive(true);
        enrollment.setEnrolledAt(LocalDateTime.now());
        CohortEnrollment saved = cohortEnrollmentRepository.save(enrollment);
        backfillOpenAssignments(student, target);

        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.UPDATE,
                "CohortEnrollment", saved.getPublicId(),
                fromCohort == null ? null : "{\"cohort\":" + json(fromCohort) + "}",
                "{\"cohort\":" + json(target.getName()) + "}");
        log.info("Student {} by admin: studentPublicId={}, from={}, to={}, by={}",
                fromCohort == null ? "enrolled" : "moved", student.getPublicId(),
                fromCohort, target.getPublicId(), caller.getPublicId());
        return saved;
    }

    // A student joining after assignments in this cohort were already
    // published must not be invisible to them — create a Submission for
    // every currently-OPEN assignment, mirroring what
    // AssignmentService.publish() does for students enrolled at publish time.
    // Skips ones that already exist (a student returning to a previous cohort).
    private void backfillOpenAssignments(User student, Cohort cohort) {
        List<Assignment> openAssignments = assignmentRepository
                .findByCohortAndStatus(cohort, AssignmentStatus.OPEN);

        int created = 0;
        for (Assignment assignment : openAssignments) {
            if (submissionRepository.findByAssignmentAndStudent(assignment, student).isPresent()) continue;
            Submission submission = new Submission();
            submission.setAssignment(assignment);
            submission.setStudent(student);
            submissionRepository.save(submission);
            created++;
        }

        if (created > 0) {
            log.info("Backfilled {} submission(s) for enrolled student: userPublicId={}, cohortPublicId={}",
                    created, student.getPublicId(), cohort.getPublicId());
        }
    }

    private static String json(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
