package com.dayo.capstone.cadence.security.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Same shape as EmailVerificationToken. tokenHash stores SHA-256 of
 * (userPublicId + ":" + code) so that two users who happen to receive
 * the same 6-digit code never produce the same stored hash, keeping the
 * unique constraint safe.
 */
@Entity
@Table(name = "phone_verification_token")
@Getter
@Setter
@NoArgsConstructor
public class PhoneVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    public boolean isValid() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }
}
