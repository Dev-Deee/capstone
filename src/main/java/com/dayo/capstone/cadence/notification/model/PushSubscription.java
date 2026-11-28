package com.dayo.capstone.cadence.notification.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One browser/device a user has allowed to receive Web Push notifications.
 *
 * Belongs to a user, not an institution: a device gets the user's
 * notifications from every institution they're in, just as the in-app
 * inbox does when they switch. The endpoint is unique — if another account
 * signs in on the same browser and subscribes, the row moves to them.
 */
@Entity
@Table(name = "push_subscription", indexes = {
        @Index(name = "idx_push_subscription_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The push service URL; its path is a capability for this device, so it's never logged.
    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    // The browser's P-256 public key and auth secret (base64url), used to encrypt each message.
    @Column(nullable = false, length = 100)
    private String p256dh;

    @Column(nullable = false, length = 50)
    private String auth;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;
}
