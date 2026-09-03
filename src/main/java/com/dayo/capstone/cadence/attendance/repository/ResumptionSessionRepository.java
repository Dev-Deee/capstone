package com.dayo.capstone.cadence.attendance.repository;

import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

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
}