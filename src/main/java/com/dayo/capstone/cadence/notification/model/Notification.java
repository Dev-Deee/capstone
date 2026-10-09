package com.dayo.capstone.cadence.notification.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One notification for one user in one institution (PRD Section 12). Not
 * audit data: read notifications are purged after 180 days.
 *
 * Rows are written by NotificationService's batch insert, not by JPA saves,
 * so they can be inserted in bulk and de-duplicated in one statement.
 */
@Entity
@Table(name = "notification", indexes = {
        // The inbox query: my notifications here, unread first / newest first.
        @Index(name = "idx_notification_inbox", columnList = "recipient_id, institution_id, read_at, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    // Where clicking it goes: a frontend route, plus the target's publicId if
    // there is one. Never an internal id.
    @Column(name = "link_route")
    private String linkRoute;

    @Column(name = "link_public_id")
    private UUID linkPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // eventId:recipientId — the same event never notifies the same person twice.
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 100)
    private String dedupeKey;
}
