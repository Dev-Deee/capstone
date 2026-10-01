package com.dayo.capstone.cadence.user.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.institution.model.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * PRD Section 3 — roles is a Set, not a single field: a user can hold
 * multiple roles simultaneously. Permission checks throughout the
 * codebase must check hasRole(X), never assume a single role.
 *
 * PRD Section 9 — general NDPR consent fields live here rather than a
 * separate table; one row per user is the natural shape for "has this
 * person agreed to X, as of when, under which version."
 *
 * GPS consent (Section 6.5) and WebAuthn capability (Section 6.12)
 * fields were removed — both features are designed but not built;
 * re-add when either is actually implemented, rather than carrying
 * unused columns on the platform's most central entity in the meantime.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    private String firstName;
    private String middleName;
    private String lastName;

    @Column(unique = true)
    private String email;

    private String phoneNumber;

    @Column(name = "password_hash")
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();

    // --- Section 9: general NDPR consent ---
    private boolean consentGiven = false;
    private LocalDateTime consentTimestamp;
    private String consentVersion;

    // --- Section 18.1: right-to-erasure via soft-delete/anonymization ---
    private boolean isDeleted = false;

    @Column(name = "institution_student_id")
    private String institutionStudentId; // nullable; the institution's own
    // existing ID/matric number, if
    // one exists — never generated
    // by Cadence

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "web_authn_capable")
    private boolean webAuthnCapable = false;

    // columnDefinition includes DEFAULT false so PostgreSQL fills existing
    // rows with false immediately on ALTER TABLE ADD COLUMN, preventing the
    // NullPointerException that hit web_authn_capable when it was added without
    // a database-level default.
    @Column(name = "phone_verified", columnDefinition = "boolean default false")
    private boolean phoneVerified = false;

    @Column(name = "account_pending")
    private boolean accountPending = true;

    // Admin-controlled on/off switch, separate from isDeleted: a deactivated
    // account keeps its data and can be re-activated; it just can't sign in.
    // DEFAULT true so existing rows are filled on ALTER TABLE ADD COLUMN.
    @Column(name = "is_active", columnDefinition = "boolean default true")
    private boolean isActive = true;

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public String getFullName() {
        return (middleName == null || middleName.isBlank())
                ? String.join(" ", firstName, lastName)
                : String.join(" ", firstName, middleName, lastName);
    }
}