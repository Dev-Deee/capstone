package com.dayo.capstone.cadence.notification.repository;

import com.dayo.capstone.cadence.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :userId AND n.institution.id = :institutionId ORDER BY n.createdAt DESC, n.id DESC")
    Page<Notification> findInbox(@Param("userId") Long userId, @Param("institutionId") Long institutionId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :userId AND n.institution.id = :institutionId AND n.readAt IS NULL")
    long countUnread(@Param("userId") Long userId, @Param("institutionId") Long institutionId);

    // Ownership is part of the lookup: someone else's notification, or one from
    // another institution, is simply not found.
    @Query("SELECT n FROM Notification n WHERE n.publicId = :publicId AND n.recipient.id = :userId AND n.institution.id = :institutionId")
    Optional<Notification> findOwned(@Param("publicId") UUID publicId, @Param("userId") Long userId, @Param("institutionId") Long institutionId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipient.id = :userId AND n.institution.id = :institutionId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("institutionId") Long institutionId, @Param("now") LocalDateTime now);

    // Retention: read notifications older than the cutoff. Unread ones are kept.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :cutoff")
    int deleteReadBefore(@Param("cutoff") LocalDateTime cutoff);

    boolean existsByDedupeKey(String dedupeKey);
}
