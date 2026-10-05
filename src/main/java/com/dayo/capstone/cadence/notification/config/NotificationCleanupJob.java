package com.dayo.capstone.cadence.notification.config;

import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Retention (PRD 18.9): read notifications are deleted 180 days after they
 * were read. Unread ones are kept. Notifications aren't audit data — AuditLog
 * keeps its own indefinite retention. Same pattern as TokenCleanupJob.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    public static final int RETENTION_DAYS = 180;

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeReadNotifications() {
        int removed = notificationRepository.deleteReadBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        log.info("NotificationCleanupJob: purged {} read notifications older than {} days", removed, RETENTION_DAYS);
    }
}
