package com.dayo.capstone.cadence.security.config;

import com.dayo.capstone.cadence.security.repository.BlacklistedTokenRepository;
import com.dayo.capstone.cadence.security.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PRD Section 18.9's retention-policy principle, extended to the two
 * token tables — neither is audit data (that's AuditLog's job,
 * indefinite by design), so both are safe to purge once naturally expired.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // Runs daily at 3 AM — low-traffic window.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        long blacklistedRemoved = blacklistedTokenRepository.deleteByExpiresAtBefore(now);
        long refreshRemoved = refreshTokenRepository.deleteByExpiresAtBefore(now);

        log.info("TokenCleanupJob: purged {} expired blacklisted tokens, {} expired refresh tokens",
                blacklistedRemoved, refreshRemoved);
    }
}