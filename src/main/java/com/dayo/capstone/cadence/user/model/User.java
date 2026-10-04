package com.dayo.capstone.cadence.user.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PRD Section 3 — a user can hold several roles at once, and (multi-tenant)
 * different roles in different institutions. Roles live on Membership;
 * hasRole(X) answers for the institution the current request is acting in.
 * Permission checks must use hasRole(X), never assume a single role.
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

    // Multi-tenant: which institutions this person belongs to, and with which
    // roles, lives on Membership — never on User. See getRoles()/hasRole().
    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private Set<Membership> memberships = new HashSet<>();

    // Platform-wide oversight (the Cadence team). Separate from any membership,
    // never granted by sign-up or invites.
    @Column(name = "is_platform_admin", nullable = false, columnDefinition = "boolean default false")
    private boolean isPlatformAdmin = false;

    // The institution a fresh login lands in when the user belongs to several.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_active_institution_id")
    private Institution lastActiveInstitution;

    private String firstName;
    private String middleName;
    private String lastName;

    @Column(unique = true)
    private String email;

    private String phoneNumber;

    @Column(name = "password_hash")
    private String passwordHash;


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

    /**
     * Roles in the institution the current request is acting in (TenantContext),
     * empty outside one. A platform admin who has entered an institution acts as
     * its SUPER_ADMIN; that applies only to them as the caller, never when they
     * are the target of someone else's check.
     */
    public Set<Role> getRoles() {
        Long institutionId = TenantContext.institutionId();
        if (institutionId == null) return Set.of();
        Set<Role> result = memberships.stream()
                .filter(m -> m.isActive() && m.getInstitution().getId().equals(institutionId))
                .flatMap(m -> m.getRoles().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        if (isPlatformAdmin && TenantContext.isPlatformAdminActing(getPublicId())) {
            result.add(Role.SUPER_ADMIN);
        }
        return result;
    }

    public boolean hasRole(Role role) {
        return getRoles().contains(role);
    }

    public Optional<Membership> membershipIn(Long institutionId) {
        return memberships.stream()
                .filter(m -> m.getInstitution().getId().equals(institutionId))
                .findFirst();
    }

    public boolean isMemberOf(Long institutionId) {
        return membershipIn(institutionId).isPresent();
    }

    public String getFullName() {
        return (middleName == null || middleName.isBlank())
                ? String.join(" ", firstName, lastName)
                : String.join(" ", firstName, middleName, lastName);
    }
}