package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByPublicId(UUID publicId);
    Optional<Submission> findByAssignmentAndStudent(Assignment assignment, User student);
    List<Submission> findByStudent(User student);
    List<Submission> findByAssignment(Assignment assignment);
    List<Submission> findByStatus(SubmissionStatus status);

    // Work handed in and waiting for the facilitator's review, across their assignments.
    long countByAssignment_FacilitatorAndStatusIn(User facilitator, Collection<SubmissionStatus> statuses);

    // ── Automatic nudges (AssignmentNudgeJob) ──────────────────────────────

    // Work not handed in on assignments in this status whose deadline is in
    // (from, to], for students still actively enrolled in the cohort, outside
    // suspended institutions, and without an extension request in one of
    // extensionStatuses.
    @Query("""
            SELECT s FROM Submission s JOIN FETCH s.assignment a JOIN FETCH a.cohort c JOIN FETCH c.institution i
            WHERE s.status IN :statuses AND a.status = :assignmentStatus
              AND a.deadline > :from AND a.deadline <= :to
              AND i.status <> :suspended
              AND EXISTS (SELECT e FROM CohortEnrollment e
                          WHERE e.student = s.student AND e.cohort = a.cohort AND e.isActive = true)
              AND NOT EXISTS (SELECT x FROM ExtensionRequest x
                              WHERE x.submission = s AND x.status IN :extensionStatuses)
            """)
    List<Submission> findNotHandedInDueBetween(@Param("statuses") Collection<SubmissionStatus> statuses,
                                               @Param("assignmentStatus") AssignmentStatus assignmentStatus,
                                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                               @Param("suspended") InstitutionStatus suspended,
                                               @Param("extensionStatuses") Collection<ExtensionRequestStatus> extensionStatuses);

    // Work sent back for a redo, still not handed in again, due again in (from, to].
    @Query("""
            SELECT s FROM Submission s JOIN FETCH s.assignment a JOIN FETCH a.cohort c JOIN FETCH c.institution i
            WHERE s.status = :redo AND s.redoDueAt > :from AND s.redoDueAt <= :to
              AND i.status <> :suspended
              AND EXISTS (SELECT e FROM CohortEnrollment e
                          WHERE e.student = s.student AND e.cohort = a.cohort AND e.isActive = true)
            """)
    List<Submission> findRedoDueBetween(@Param("redo") SubmissionStatus redo,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                        @Param("suspended") InstitutionStatus suspended);

    /** Handed-in work waiting for one facilitator's review in one institution. */
    interface WaitingForReview {
        Long getFacilitatorId();
        Long getInstitutionId();
        UUID getInstitutionPublicId();
        Long getSubmissions();
        Long getAssignments();
    }

    @Query("""
            SELECT a.facilitator.id AS facilitatorId, i.id AS institutionId, i.publicId AS institutionPublicId,
                   COUNT(s) AS submissions, COUNT(DISTINCT a.id) AS assignments
            FROM Submission s JOIN s.assignment a JOIN a.cohort c JOIN c.institution i
            WHERE s.status IN :statuses AND i.status <> :suspended
            GROUP BY a.facilitator.id, i.id, i.publicId
            """)
    List<WaitingForReview> countWaitingForReview(@Param("statuses") Collection<SubmissionStatus> statuses,
                                                 @Param("suspended") InstitutionStatus suspended);
}
