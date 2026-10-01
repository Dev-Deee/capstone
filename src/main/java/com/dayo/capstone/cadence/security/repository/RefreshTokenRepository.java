package com.dayo.capstone.cadence.security.repository;

import com.dayo.capstone.cadence.security.model.RefreshToken;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserAndRevokedFalse(User user);
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.user = :user AND r.revoked = false")
    void revokeAllForUser(User user, LocalDateTime now);
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
    List<RefreshToken> findByUserAndRevokedFalseAndExpiresAtAfter(User user, LocalDateTime now);
    Optional<RefreshToken> findByPublicId(UUID publicId);
    List<RefreshToken> findByUserAndUserAgentAndRevokedFalse(User user, String userAgent);
}