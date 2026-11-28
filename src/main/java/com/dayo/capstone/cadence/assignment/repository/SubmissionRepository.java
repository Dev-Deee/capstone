package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // The same, across a whole institution (a SUPER_ADMIN's view).
    long countByAssignment_Cohort_InstitutionAndStatusIn(Institution institution, Collection<SubmissionStatus> statuses);

    // Per-assignment counts of submissions in the given statuses, for one institution: [assignmentId, count].
    @Query("select s.assignment.id, count(s) from Submission s "
            + "where s.assignment.cohort.institution = :institution and s.status in :statuses group by s.assignment.id")
    List<Object[]> countByAssignmentInInstitution(@Param("institution") Institution institution,
                                                  @Param("statuses") Collection<SubmissionStatus> statuses);
}
