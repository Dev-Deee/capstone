package com.dayo.capstone.cadence.course.repository;

import com.dayo.capstone.cadence.course.enums.CourseStatus;
import com.dayo.capstone.cadence.course.model.Course;
import com.dayo.capstone.cadence.institution.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByPublicId(UUID publicId);
    List<Course> findByInstitutionOrderByCreatedAtDesc(Institution institution);
    List<Course> findByInstitutionAndStatusOrderByPublishedAtDesc(Institution institution, CourseStatus status);
    List<Course> findByStatusAndPublishAtLessThanEqual(CourseStatus status, LocalDateTime time);
}
