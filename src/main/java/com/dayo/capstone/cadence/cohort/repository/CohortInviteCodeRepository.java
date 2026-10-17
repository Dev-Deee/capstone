package com.dayo.capstone.cadence.cohort.repository;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortInviteCodeRepository extends JpaRepository<CohortInviteCode, Long> {
    Optional<CohortInviteCode> findByCode(String code);
    Optional<CohortInviteCode> findByPublicId(UUID publicId);
    List<CohortInviteCode> findByCohortOrderByCreatedAtDesc(Cohort cohort);
}
