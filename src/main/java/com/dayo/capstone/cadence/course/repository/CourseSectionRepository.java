package com.dayo.capstone.cadence.course.repository;

import com.dayo.capstone.cadence.course.model.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    Optional<CourseSection> findByPublicId(UUID publicId);
}
