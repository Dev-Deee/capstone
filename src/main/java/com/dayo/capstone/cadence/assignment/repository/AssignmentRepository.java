package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByPublicId(UUID publicId);
    List<Assignment> findByCohort(Cohort cohort);
    List<Assignment> findByFacilitator(User facilitator);
    List<Assignment> findByCohortAndStatus(Cohort cohort, AssignmentStatus status);
    List<Assignment> findByStatusAndDeadlineBefore(AssignmentStatus status, LocalDateTime deadline);
}
