package com.dayo.capstone.cadence.institution.repository;

import com.dayo.capstone.cadence.institution.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    // Public-facing lookup only — internal Long id is never queried
    // directly from outside the repository layer itself.
    Optional<Institution> findByPublicId(UUID publicId);

    boolean existsByPublicId(UUID publicId);

    Optional<Institution> findByName(String name);
    Optional<Institution> findBySlug(String slug);
    boolean existsBySlug(String slug);
}