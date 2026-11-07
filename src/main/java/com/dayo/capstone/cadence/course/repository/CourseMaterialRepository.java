package com.dayo.capstone.cadence.course.repository;

import com.dayo.capstone.cadence.course.model.CourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, Long> {
    Optional<CourseMaterial> findByPublicId(UUID publicId);
}
