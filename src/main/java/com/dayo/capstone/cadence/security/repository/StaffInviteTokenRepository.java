package com.dayo.capstone.cadence.security.repository;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.security.model.StaffInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffInviteTokenRepository extends JpaRepository<StaffInviteToken, Long> {
    Optional<StaffInviteToken> findByTokenHash(String tokenHash);

    // Open invites for this person to this institution — superseded when a new one is sent.
    List<StaffInviteToken> findByInstitutionAndEmailIgnoreCaseAndUsedFalse(Institution institution, String email);
}