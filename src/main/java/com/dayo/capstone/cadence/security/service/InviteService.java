package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.admin.repository.AdminRoleRepository;
import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.service.CohortEnrollmentService;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.exception.ConflictException;
import com.dayo.capstone.cadence.common.exception.ForbiddenException;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.service.MembershipService;
import com.dayo.capstone.cadence.security.dto.request.AcceptStaffInviteRequest;
import com.dayo.capstone.cadence.security.dto.request.CreateStaffInviteRequest;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.dto.response.InvitePreviewResponse;
import com.dayo.capstone.cadence.security.model.StaffInviteToken;
import com.dayo.capstone.cadence.security.repository.StaffInviteTokenRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.InviteAcceptedEvent;

/**
 * Invitations into one institution (multi-tenant replacement for the old
 * staff-invite flow, which created a User the moment someone was invited).
 *
 * Who may invite whom, in the caller's current institution:
 *   SUPER_ADMIN  — anyone, including a co-SUPER_ADMIN (the only way to add an
 *                  owner, which the last-owner rule relies on)
 *   ADMIN        — FACILITATOR freely; ADMIN only with permissions they hold
 *                  themselves; STUDENT with MANAGE_ENROLLMENTS. Never SUPER_ADMIN.
 *
 * Acceptance:
 *   brand-new invitee  → token + password creates the account and membership
 *   existing account   → must sign in and accept as that account; the signed-in
 *                        email must equal the invited email (forwarded links are refused)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private static final Set<Role> INVITABLE = EnumSet.of(Role.STUDENT, Role.FACILITATOR, Role.ADMIN, Role.SUPER_ADMIN);

    private final StaffInviteTokenRepository inviteRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final AdminRoleRepository adminRoleRepository;
    private final UserAdminRoleAssignmentRepository userAdminRoleAssignmentRepository;
    private final AdminPermissionService adminPermissionService;
    private final CohortService cohortService;
    private final CohortEnrollmentService cohortEnrollmentService;
    private final DomainEvents domainEvents;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;
    private final AuditLogService auditLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Create ────────────────────────────────────────────────────────────

    @Transactional
    public void create(CreateStaffInviteRequest request, Institution institution, User caller) {
        Role role = request.getTargetRole();
        if (!INVITABLE.contains(role)) {
            throw new IllegalArgumentException("Invites can be for STUDENT, FACILITATOR, ADMIN or SUPER_ADMIN");
        }
        String email = normalize(request.getEmail());

        // Scope every referenced record to this institution.
        Cohort cohort = request.getCohortPublicId() == null || request.getCohortPublicId().isBlank() ? null
                : cohortService.findEntityByPublicIdScopedToInstitution(UUID.fromString(request.getCohortPublicId()), institution);
        AdminRole adminRole = null;
        if (role == Role.ADMIN) {
            if (request.getAdminRolePublicId() == null || request.getAdminRolePublicId().isBlank()) {
                throw new IllegalArgumentException("Choose an admin role for an ADMIN invite");
            }
            adminRole = adminRoleRepository.findByPublicId(UUID.fromString(request.getAdminRolePublicId()))
                    .filter(r -> r.getInstitution().getId().equals(institution.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Admin role not found"));
        }

        authorize(role, adminRole, cohort, caller);

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent() && existing.get().isMemberOf(institution.getId())) {
            throw new IllegalArgumentException(email + " is already a member of " + institution.getName());
        }
        if (existing.isEmpty()) {
            if (blank(request.getFirstName()) || blank(request.getLastName()) || blank(request.getPhoneNumber())) {
                throw new IllegalArgumentException("First name, last name and phone number are required for someone without a Cadence account");
            }
        }

        // A fresh invite replaces any earlier open one for the same person here.
        inviteRepository.findByInstitutionAndEmailIgnoreCaseAndUsedFalse(institution, email).forEach(old -> {
            old.setUsed(true);
            inviteRepository.save(old);
        });

        String rawToken = UUID.randomUUID().toString();
        StaffInviteToken invite = new StaffInviteToken();
        invite.setInstitution(institution);
        invite.setEmail(email);
        invite.setRole(role);
        invite.setFirstName(trim(request.getFirstName()));
        invite.setMiddleName(trim(request.getMiddleName()));
        invite.setLastName(trim(request.getLastName()));
        invite.setPhoneNumber(trim(request.getPhoneNumber()));
        invite.setInvitedBy(caller);
        invite.setAdminRole(adminRole);
        invite.setCohort(cohort);
        invite.setTokenHash(jwtService.hashToken(rawToken));
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        inviteRepository.save(invite);

        String link = frontendUrl + "/accept-invite?token=" + rawToken;
        String roleLabel = role.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String body = existing.isPresent()
                ? "You've been invited to join " + institution.getName() + " on Cadence as a " + roleLabel + ".\n\n"
                  + "Sign in with your existing Cadence account to accept (valid 7 days):\n" + link
                : "You've been invited to join " + institution.getName() + " on Cadence as a " + roleLabel + ".\n\n"
                  + "Set your password to create your account (valid 7 days):\n" + link;
        emailService.send(email, "You've been invited to " + institution.getName() + " on Cadence", body);

        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.CREATE,
                "Invite", invite.getPublicId(), null, "{\"role\":\"" + role + "\"}");
        log.info("Invite created: institutionPublicId={}, role={}, existingAccount={}",
                institution.getPublicId(), role, existing.isPresent());
    }

    private void authorize(Role role, AdminRole adminRole, Cohort cohort, User caller) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return;
        switch (role) {
            case SUPER_ADMIN -> throw new ForbiddenException("Only a super admin can invite another super admin");
            case FACILITATOR -> { /* admins may invite facilitators freely */ }
            case STUDENT -> {
                if (cohort != null) {
                    adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_ENROLLMENTS, cohort);
                } else {
                    adminPermissionService.requirePermission(caller, Permission.MANAGE_ENROLLMENTS);
                }
            }
            case ADMIN -> {
                // The invited role's permissions must be a subset of what the caller
                // holds here, and a cohort-scoped grant must be within the caller's scope.
                List<UserAdminRoleAssignment> grants = adminPermissionService.activeGrantsInCurrentInstitution(caller);
                if (grants.isEmpty()) {
                    throw new ForbiddenException("You have no admin permissions in this institution");
                }
                Set<Permission> held = grants.stream()
                        .flatMap(a -> a.getAdminRole().getPermissions().stream())
                        .collect(Collectors.toSet());
                if (!held.containsAll(adminRole.getPermissions())) {
                    Set<Permission> excess = new HashSet<>(adminRole.getPermissions());
                    excess.removeAll(held);
                    throw new ForbiddenException("You can't grant permissions you don't hold: " + excess);
                }
                if (cohort != null && grants.stream().noneMatch(a -> a.getCohort() == null || a.getCohort().getId().equals(cohort.getId()))) {
                    throw new ForbiddenException("Your admin permissions don't cover that cohort");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported role");
        }
    }

    // ── Preview (public) ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String rawToken) {
        StaffInviteToken invite = findValid(rawToken);
        boolean accountExists = activeAccount(invite).isPresent();
        return InvitePreviewResponse.builder()
                .institutionName(invite.getInstitution().getName())
                .institutionSlug(invite.getInstitution().getSlug())
                .role(invite.getRole().name())
                .email(invite.getEmail())
                .firstName(invite.getFirstName())
                .accountExists(accountExists)
                .build();
    }

    // ── Accept: brand-new invitee (public) ────────────────────────────────

    @Transactional
    public AuthResponse acceptAsNewAccount(AcceptStaffInviteRequest request, String userAgent, String ipAddress) {
        StaffInviteToken invite = findValid(request.getToken());
        if (activeAccount(invite).isPresent()) {
            throw new ConflictException("You already have a Cadence account. Sign in with it to accept this invite.");
        }

        User user;
        if (invite.getUser() != null) {
            // Invite from before multi-tenancy: its pending account (and membership) already exist.
            user = invite.getUser();
        } else {
            user = userRepository.findByEmailIgnoreCase(invite.getEmail()).orElseGet(User::new);
            user.setEmail(invite.getEmail());
            user.setFirstName(invite.getFirstName());
            user.setMiddleName(invite.getMiddleName());
            user.setLastName(invite.getLastName());
            user.setPhoneNumber(invite.getPhoneNumber());
        }
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountPending(false);
        user.setEmailVerified(true); // following the emailed link verifies the address
        user = userRepository.save(user);

        grant(invite, user);
        log.info("Invite accepted (new account): userPublicId={}, institutionPublicId={}",
                user.getPublicId(), invite.getInstitution().getPublicId());
        return sessionTokenService.issue(user, invite.getInstitution(), userAgent, ipAddress);
    }

    // ── Accept: existing account (authenticated) ──────────────────────────

    @Transactional
    public AuthResponse acceptAsExistingAccount(String rawToken, User caller, String userAgent, String ipAddress) {
        StaffInviteToken invite = findValid(rawToken);
        if (caller.getEmail() == null || !caller.getEmail().equalsIgnoreCase(invite.getEmail())) {
            throw new ForbiddenException("This invite was sent to a different email address. "
                    + "Sign in as " + invite.getEmail() + " to accept it.");
        }
        if (caller.isMemberOf(invite.getInstitution().getId())) {
            invite.setUsed(true);
            inviteRepository.save(invite);
            throw new IllegalArgumentException("You're already a member of " + invite.getInstitution().getName());
        }
        grant(invite, caller);
        log.info("Invite accepted (existing account): userPublicId={}, institutionPublicId={}",
                caller.getPublicId(), invite.getInstitution().getPublicId());
        return sessionTokenService.issue(caller, invite.getInstitution(), userAgent, ipAddress);
    }

    // Creates the membership (unless a legacy invite already has one) plus the
    // role's extras, then spends the invite.
    private void grant(StaffInviteToken invite, User user) {
        Institution institution = invite.getInstitution();
        if (!user.isMemberOf(institution.getId())) {
            membershipService.add(user, institution, EnumSet.of(invite.getRole()));
        }
        if (invite.getAdminRole() != null) {
            UserAdminRoleAssignment assignment = new UserAdminRoleAssignment();
            assignment.setUser(user);
            assignment.setAdminRole(invite.getAdminRole());
            assignment.setCohort(invite.getCohort());
            assignment.setAssignedAt(LocalDateTime.now());
            userAdminRoleAssignmentRepository.save(assignment);
        }
        if (invite.getRole() == Role.STUDENT && invite.getCohort() != null) {
            // One active enrollment per student (platform-wide, for now).
            if (cohortEnrollmentRepository.findByStudentAndIsActiveTrue(user).isPresent()) {
                throw new IllegalArgumentException("You're already enrolled in a cohort. Ask the institution to invite you without one.");
            }
            cohortEnrollmentService.enroll(user, invite.getCohort());
        }
        invite.setUsed(true);
        inviteRepository.save(invite);
        domainEvents.publish(new InviteAcceptedEvent(institution.getId(), user.getId(),
                invite.getInvitedBy() == null ? null : invite.getInvitedBy().getId(),
                user.getPublicId(), user.getFullName(), invite.getRole().name()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private StaffInviteToken findValid(String rawToken) {
        return inviteRepository.findByTokenHash(jwtService.hashToken(rawToken == null ? "" : rawToken))
                .filter(StaffInviteToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("This invite link is invalid or has expired"));
    }

    // An account the invitee can already sign in with (legacy pending accounts don't count).
    private Optional<User> activeAccount(StaffInviteToken invite) {
        if (invite.getUser() != null) return Optional.empty();
        return userRepository.findByEmailIgnoreCase(invite.getEmail())
                .filter(u -> !u.isDeleted() && u.getPasswordHash() != null && !u.isAccountPending());
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
