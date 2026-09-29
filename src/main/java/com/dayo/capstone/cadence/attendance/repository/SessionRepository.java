package com.dayo.capstone.cadence.attendance.repository;

import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByPublicId(UUID publicId);
    Optional<Session> findBySessionCode(String sessionCode);
    Optional<Session> findByQrToken(String qrToken);

    // Returns sessions that are marked open and not yet expired — used by
    // TOTP attendance validation to find candidates for a student's cohort.
    @Query("SELECT s FROM Session s WHERE s.cohort = :cohort AND s.isOpen = true AND s.expiresAt > CURRENT_TIMESTAMP")
    List<Session> findOpenByCohort(@Param("cohort") Cohort cohort);
}