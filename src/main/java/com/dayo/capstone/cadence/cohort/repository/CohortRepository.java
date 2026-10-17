package com.dayo.capstone.cadence.cohort.repository;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortRepository extends JpaRepository<Cohort, Long> {
    Optional<Cohort> findByPublicId(UUID publicId);
    List<Cohort> findByIsActiveTrue();
    List<Cohort> findByInstitution(Institution institution);
}