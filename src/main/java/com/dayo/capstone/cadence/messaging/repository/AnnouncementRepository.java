package com.dayo.capstone.cadence.messaging.repository;

import com.dayo.capstone.cadence.messaging.model.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("SELECT a FROM Announcement a WHERE a.publicId = :publicId AND a.institution.id = :institutionId")
    Optional<Announcement> findScoped(@Param("publicId") UUID publicId, @Param("institutionId") Long institutionId);

    // A student's feed: posted to any cohort they're actively enrolled in here.
    @Query(value = "SELECT DISTINCT a FROM Announcement a JOIN a.cohorts c, CohortEnrollment e "
            + "WHERE a.institution.id = :institutionId AND e.cohort = c AND e.student.id = :studentId AND e.isActive = true "
            + "ORDER BY a.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT a) FROM Announcement a JOIN a.cohorts c, CohortEnrollment e "
                    + "WHERE a.institution.id = :institutionId AND e.cohort = c AND e.student.id = :studentId AND e.isActive = true")
    Page<Announcement> findFeed(@Param("institutionId") Long institutionId, @Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT a FROM Announcement a WHERE a.institution.id = :institutionId AND a.author.id = :authorId ORDER BY a.createdAt DESC")
    Page<Announcement> findByAuthor(@Param("institutionId") Long institutionId, @Param("authorId") Long authorId, Pageable pageable);

    @Query("SELECT a FROM Announcement a WHERE a.institution.id = :institutionId ORDER BY a.createdAt DESC")
    Page<Announcement> findAllHere(@Param("institutionId") Long institutionId, Pageable pageable);
}
