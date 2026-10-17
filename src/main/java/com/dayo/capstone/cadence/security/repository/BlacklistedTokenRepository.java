package com.dayo.capstone.cadence.security.repository;

import com.dayo.capstone.cadence.security.model.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {
    boolean existsByJti(String jti);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}