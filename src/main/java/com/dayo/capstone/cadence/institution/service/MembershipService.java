package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.security.repository.RefreshTokenRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Every change to someone's place in an institution goes through here, so the
 * orphaned-tenant rule is enforced in one spot: an institution must always keep
 * at least one active SUPER_ADMIN. Demoting, suspending, removing or leaving as
 * the last one is refused — promote or invite another owner first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final UserAdminRoleAssignmentRepository userAdminRoleAssignmentRepository;
    private final AuditLogService auditLogService;

    /** Adds a membership and keeps the user's in-memory collection in step (used right away to issue tokens). */
    @Transactional
    public Membership add(User user, Institution institution, Set<Role> roles) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("A member needs at least one role");
        }
        if (membershipRepository.existsByUserAndInstitution(user, institution)) {
            throw new IllegalArgumentException("Already a member of this institution");
        }
        Membership saved = membershipRepository.save(Membership.of(user, institution, roles));
        user.getMemberships().add(saved);
        log.info("Membership added: userPublicId={}, institutionPublicId={}, roles={}",
                user.getPublicId(), institution.getPublicId(), roles);
        return saved;
    }

    public Membership require(User user, Institution institution) {
        return membershipRepository.findByUserAndInstitution(user, institution)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Replace a member's roles. Owner-only (enforced by the controller); can't drop the last owner. */
    @Transactional
    public Membership updateRoles(User target, Institution institution, Set<Role> roles, User caller) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one role");
        }
        Membership membership = require(target, institution);
        Set<Role> before = EnumSet.copyOf(membership.getRoles());
        if (before.contains(Role.SUPER_ADMIN) && !roles.contains(Role.SUPER_ADMIN)) {
            requireAnotherOwner(membership, institution, "demote");
        }
        membership.setRoles(EnumSet.copyOf(roles));
        Membership saved = membershipRepository.save(membership);
        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.UPDATE,
                "Membership", saved.getPublicId(), "{\"roles\":\"" + before + "\"}", "{\"roles\":\"" + roles + "\"}");
        return saved;
    }

    /** Suspend or restore someone's access to this institution only. */
    @Transactional
    public Membership setActive(User target, Institution institution, boolean active, User caller) {
        Membership membership = require(target, institution);
        if (membership.isActive() == active) return membership;
        if (!active && membership.hasRole(Role.SUPER_ADMIN)) {
            requireAnotherOwner(membership, institution, "suspend");
        }
        membership.setActive(active);
        Membership saved = membershipRepository.save(membership);
        if (!active) {
            refreshTokenRepository.revokeAllForUserInInstitution(target, institution, LocalDateTime.now());
        }
        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.UPDATE,
                "Membership", saved.getPublicId(), "{\"active\":" + !active + "}", "{\"active\":" + active + "}");
        return saved;
    }

    /**
     * Removes someone from this institution only — their account and their
     * memberships elsewhere are untouched. Their history here (attendance,
     * submissions, audit) is kept; their active enrollment and admin-role
     * grants in this institution end, and their sessions here are revoked.
     */
    @Transactional
    public void remove(User target, Institution institution, User caller) {
        Membership membership = require(target, institution);
        if (membership.hasRole(Role.SUPER_ADMIN)) {
            requireAnotherOwner(membership, institution, target.getId().equals(caller.getId()) ? "leave" : "remove");
        }
        cohortEnrollmentRepository.findByStudentAndIsActiveTrue(target)
                .filter(e -> e.getCohort().getInstitution().getId().equals(institution.getId()))
                .ifPresent(e -> {
                    e.setActive(false);
                    cohortEnrollmentRepository.save(e);
                });
        userAdminRoleAssignmentRepository.findByUserAndIsActiveTrue(target).stream()
                .filter(a -> a.getAdminRole().getInstitution().getId().equals(institution.getId()))
                .forEach(a -> {
                    a.setActive(false);
                    userAdminRoleAssignmentRepository.save(a);
                });
        refreshTokenRepository.revokeAllForUserInInstitution(target, institution, LocalDateTime.now());
        target.getMemberships().remove(membership);
        membershipRepository.delete(membership);
        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.DELETE,
                "Membership", membership.getPublicId());
        log.info("Membership removed: userPublicId={}, institutionPublicId={}, by={}",
                target.getPublicId(), institution.getPublicId(), caller.getPublicId());
    }

    /** Whether removing this user's ownership anywhere would orphan an institution. */
    public boolean isSoleOwnerSomewhere(User user) {
        return user.getMemberships().stream()
                .filter(m -> m.isActive() && m.hasRole(Role.SUPER_ADMIN))
                .anyMatch(m -> membershipRepository.countActiveWithRole(m.getInstitution(), Role.SUPER_ADMIN) <= 1);
    }

    // Uses the membership's own institution (loaded in this transaction): the
    // Institution passed in may be a detached proxy from the caller's user.
    private void requireAnotherOwner(Membership membership, Institution institution, String action) {
        if (membership.isActive() && membershipRepository.countActiveWithRole(institution, Role.SUPER_ADMIN) <= 1) {
            throw new IllegalArgumentException("You can't " + action + " the only super admin of "
                    + membership.getInstitution().getName() + ". Make someone else a super admin first.");
        }
    }

    /** Thrown as 403 elsewhere; kept here so callers share the wording. */
    public static AccessDeniedException ownersOnly() {
        return new AccessDeniedException("Only a super admin of this institution can do that");
    }
}
