package com.dayo.capstone.cadence.institution.repository;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByPublicId(UUID publicId);
    List<Subject> findByInstitution(Institution institution);
}