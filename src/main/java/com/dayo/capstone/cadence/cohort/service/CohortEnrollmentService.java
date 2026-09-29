package com.dayo.capstone.cadence.cohort.service;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEnrollmentService {

    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    // Single entry point for "put this student in this cohort" — used by
    // both self-registration (AuthService, via invite code) and admin-driven
    // manual enrollment (CohortController). Centralized so the submission
    // backfill below only has to be written and maintained once.
    @Transactional
    public CohortEnrollment enroll(User student, Cohort cohort) {
        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setStudent(student);
        enrollment.setCohort(cohort);
        enrollment.setEnrolledAt(LocalDateTime.now());
        CohortEnrollment saved = cohortEnrollmentRepository.save(enrollment);

        // A student joining after assignments in this cohort were already
        // published must not be invisible to them — create a Submission for
        // every currently-OPEN assignment, mirroring what
        // AssignmentService.publish() does for students enrolled at publish time.
        List<Assignment> openAssignments = assignmentRepository
                .findByCohortAndStatus(cohort, AssignmentStatus.OPEN);

        for (Assignment assignment : openAssignments) {
            Submission submission = new Submission();
            submission.setAssignment(assignment);
            submission.setStudent(student);
            submissionRepository.save(submission);
        }

        if (!openAssignments.isEmpty()) {
            log.info("Backfilled {} submission(s) for enrolled student: userPublicId={}, cohortPublicId={}",
                    openAssignments.size(), student.getPublicId(), cohort.getPublicId());
        }

        return saved;
    }
}