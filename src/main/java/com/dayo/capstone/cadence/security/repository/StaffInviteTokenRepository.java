package com.dayo.capstone.cadence.security.repository;

import com.dayo.capstone.cadence.security.model.StaffInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffInviteTokenRepository extends JpaRepository<StaffInviteToken, Long> {
    Optional<StaffInviteToken> findByTokenHash(String tokenHash);
}