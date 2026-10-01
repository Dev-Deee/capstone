package com.dayo.capstone.cadence.attendance.repository;

import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumptionSessionRepository extends JpaRepository<ResumptionSession, Long> {
    Optional<ResumptionSession> findByPublicId(UUID publicId);
    Optional<ResumptionSession> findByResumptionCode(String code);
    Optional<ResumptionSession> findByCohortAndResumptionDate(Cohort cohort, LocalDate date);
    // ResumptionSessionRepository.java — add
    List<ResumptionSession> findByCohort_InstitutionAndResumptionDate(Institution institution, LocalDate date);

    List<ResumptionSession> findByResumptionDateAndCohort_Institution(LocalDate date, Institution institution);

    Optional<ResumptionSession> findByQrToken(String qrToken);

    @Query("SELECT r FROM ResumptionSession r WHERE r.cohort = :cohort AND r.isOpen = true AND r.expiresAt > CURRENT_TIMESTAMP")
    List<ResumptionSession> findOpenByCohort(@Param("cohort") Cohort cohort);

    // Attendance history — all sessions for a cohort, newest first.
    List<ResumptionSession> findByCohortOrderByResumptionDateDesc(Cohort cohort);

    // Sessions for a cohort on or after a given date — respects late-joiner enrolledAt.
    @Query("SELECT r FROM ResumptionSession r WHERE r.cohort = :cohort AND r.resumptionDate >= :since ORDER BY r.resumptionDate DESC")
    List<ResumptionSession> findByCohortSince(
            @Param("cohort") Cohort cohort,
            @Param("since") LocalDate since);
}