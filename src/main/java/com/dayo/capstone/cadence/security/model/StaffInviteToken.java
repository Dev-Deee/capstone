package com.dayo.capstone.cadence.security.model;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff_invite_token")
@Getter
@Setter
@NoArgsConstructor
public class StaffInviteToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id")
    private AdminRole adminRole; // nullable — only set for targetRole = ADMIN

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort; // nullable — scope for the AdminRole grant, if any

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    public boolean isValid() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }
}