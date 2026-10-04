package com.dayo.capstone.cadence.security.model;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An invitation to join one institution with one role. Multi-tenant: the
 * invitee may not have a Cadence account yet, so the invite carries the email
 * and the details needed to create one; the account (if new) and the
 * Membership are created only when the invite is accepted. Inviting someone
 * no longer creates a User up front, so self-serve sign-up and pending
 * invites can't collide on the same email.
 *
 * user is set only on invites created before this change (whose pending
 * account already exists).
 */
@Entity
@Table(name = "staff_invite_token")
@Getter
@Setter
@NoArgsConstructor
public class StaffInviteToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // Profile details for an invitee who has no account yet.
    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private User invitedBy;

    // Legacy only — see class comment.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id")
    private AdminRole adminRole; // only for role = ADMIN

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort; // ADMIN: scope of the AdminRole grant; STUDENT: cohort to enroll in

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    public boolean isValid() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }
}
