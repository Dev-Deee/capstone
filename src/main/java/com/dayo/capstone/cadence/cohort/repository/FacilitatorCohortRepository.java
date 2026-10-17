package com.dayo.capstone.cadence.cohort.repository;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilitatorCohortRepository extends JpaRepository<FacilitatorCohort, Long> {
    List<FacilitatorCohort> findByFacilitatorAndIsActiveTrue(User facilitator);
    Optional<FacilitatorCohort> findByFacilitatorAndCohortAndSubject(User facilitator, Cohort cohort, Subject subject);

    // Add — checks ANY active assignment to the cohort, regardless of subject
    boolean existsByFacilitatorAndCohortAndIsActiveTrue(User facilitator, Cohort cohort);
}