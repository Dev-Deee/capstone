package com.dayo.capstone.cadence.cohort.repository;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CohortEnrollmentRepository extends JpaRepository<CohortEnrollment, Long> {
    Optional<CohortEnrollment> findByStudentAndCohort(User student, Cohort cohort);
    long countByCohortAndIsActiveTrue(Cohort cohort);
}