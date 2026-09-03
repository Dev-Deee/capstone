package com.dayo.capstone.cadence.cohort.repository;

import com.dayo.capstone.cadence.cohort.model.CohortInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CohortInviteCodeRepository extends JpaRepository<CohortInviteCode, Long> {
    Optional<CohortInviteCode> findByCode(String code);
}