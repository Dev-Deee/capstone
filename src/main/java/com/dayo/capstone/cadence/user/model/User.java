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
 * PRD Section 3 — roles is a Set, not a single field: a user can be both
 * FACILITATOR and MANAGEMENT simultaneously. Permission checks throughout
 * the codebase must check hasRole(X), never assume a single role.
 *
 * PRD Sections 9, 6.5, 6.12 — consent and capability fields live here
 * rather than a separate table; one row per user is the natural shape
 * for "has this person agreed to X, as of when, under which version."
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

    // --- Section 6.5: GPS / presence consent (distinct from general consent above) ---
    private boolean gpsConsentGiven = false;
    private LocalDateTime gpsConsentTimestamp;
    private String gpsConsentVersion;

    // --- Section 6.12: WebAuthn capability, detected not self-reported ---
    private boolean webAuthnCapable = false;
    private LocalDateTime webAuthnRegisteredAt;

    // --- Section 18.1: right-to-erasure via soft-delete/anonymization ---
    private boolean isDeleted = false;

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    @Column(name = "institution_student_id")
    private String institutionStudentId;   // nullable; the institution's own
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

    // Add to User.java
    @Column(name = "account_pending")
    private boolean accountPending = true;

    public String getFullName() {
        return (middleName == null || middleName.isBlank())
                ? String.join(" ", firstName, lastName)
                : String.join(" ", firstName, middleName, lastName);
    }
}