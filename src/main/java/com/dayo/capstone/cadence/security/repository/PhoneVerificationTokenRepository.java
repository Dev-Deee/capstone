package com.dayo.capstone.cadence.security.repository;

import com.dayo.capstone.cadence.security.model.PhoneVerificationToken;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhoneVerificationTokenRepository extends JpaRepository<PhoneVerificationToken, Long> {
    Optional<PhoneVerificationToken> findByTokenHash(String tokenHash);
    List<PhoneVerificationToken> findByUser(User user);
}
