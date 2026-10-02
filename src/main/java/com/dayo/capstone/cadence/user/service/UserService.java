package com.dayo.capstone.cadence.user.service;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.service.FileStorageService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.common.exception.ForbiddenException;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.institution.service.MembershipService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.repository.RefreshTokenRepository;
import com.dayo.capstone.cadence.user.dto.request.UpdateUserRequest;
import com.dayo.capstone.cadence.user.dto.response.UserResponse;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InstitutionService institutionService;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final AdminPermissionService adminPermissionService;
    private final MembershipRepository membershipRepository;
    private final MembershipService membershipService;
    private final FacilitatorCohortRepository facilitatorCohortRepository;

    @Transactional
    public UserResponse updateProfileImage(UUID userPublicId, byte[] imageBytes, String filename) {
        User user = findEntityByPublicId(userPublicId);

        // Delete the old image first if one exists — avoids orphaned files
        // accumulating in Cloudinary storage every time a user updates their photo.
        if (user.getProfileImageUrl() != null) {
            fileStorageService.delete(user.getProfileImageUrl());
        }

        String url = fileStorageService.upload(imageBytes, filename, "profile-images");
        user.setProfileImageUrl(url);
        userRepository.save(user);

        log.info("Profile image updated: userPublicId={}", userPublicId);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getByPublicId(UUID publicId) {
        return toResponse(findEntityByPublicId(publicId));
    }

    // The members of the caller's institution — the directory never crosses tenants.
    @Transactional(readOnly = true)
    public List<UserResponse> getAll(Institution institution) {
        return membershipRepository.findMembers(institution).stream()
                .map(Membership::getUser)
                .map(this::toResponse)
                .toList();
    }

    // Students actively enrolled in any cohort the facilitator actively teaches.
    @Transactional(readOnly = true)
    public List<UserResponse> getStudentsForFacilitator(User facilitator) {
        return facilitatorCohortRepository.findByFacilitatorAndIsActiveTrue(facilitator).stream()
                .map(FacilitatorCohort::getCohort)
                .distinct()
                .flatMap(cohort -> cohortEnrollmentRepository.findByCohortAndIsActiveTrue(cohort).stream())
                .map(CohortEnrollment::getStudent)
                .filter(s -> !s.isDeleted())
                .distinct()
                .sorted(java.util.Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    // ── User management ────────────────────────────────────────────────────
    // Who may act on whom:
    //   SUPER_ADMIN               — any user in their institution
    //   ADMIN with MANAGE_USERS   — students and facilitators only (never admins/super admins)
    //   FACILITATOR               — activate/deactivate only, students in cohorts they teach
    // Every method resolves the target inside the caller's institution and writes
    // an AuditLog entry. Multi-tenant: suspending and removing act on the
    // target's membership HERE only; their account and other institutions are
    // untouched. MembershipService refuses anything that would leave the
    // institution without an active SUPER_ADMIN.

    // Remove/suspend-level management — admin tiers only.
    public void requireCanManage(User caller, User target) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return;
        if (isUserAdmin(caller)) {
            requireNotStaffAdmin(target);
            return;
        }
        throw new AccessDeniedException("You don't have permission to manage this user");
    }

    // Editing someone's profile (name, phone, email) changes it everywhere they
    // belong, so an institution may only do it for people who belong to it alone.
    public void requireCanEdit(User caller, User target) {
        requireCanManage(caller, target);
        Long here = TenantContext.institutionId();
        boolean elsewhere = target.getMemberships().stream()
                .anyMatch(m -> !m.getInstitution().getId().equals(here));
        if (elsewhere) {
            throw new ForbiddenException("This person also belongs to another institution, so only they can change their profile.");
        }
    }

    // Activate/deactivate — admin tiers, plus a facilitator for their own students.
    private void requireCanToggle(User caller, User target) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return;
        if (isUserAdmin(caller)) {
            requireNotStaffAdmin(target);
            return;
        }
        if (caller.hasRole(Role.FACILITATOR) && teachesStudent(caller, target)) return;
        throw new AccessDeniedException("You can only activate or deactivate students in cohorts you teach");
    }

    private boolean isUserAdmin(User caller) {
        return caller.hasRole(Role.ADMIN) && adminPermissionService.hasPermission(caller, Permission.MANAGE_USERS);
    }

    private static void requireNotStaffAdmin(User target) {
        if (target.hasRole(Role.ADMIN) || target.hasRole(Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Only a super admin can manage admin accounts");
        }
    }

    private boolean teachesStudent(User facilitator, User target) {
        if (!target.getRoles().equals(java.util.Set.of(Role.STUDENT))) return false;
        return cohortEnrollmentRepository.findByStudentAndIsActiveTrue(target)
                .map(e -> facilitatorCohortRepository.existsByFacilitatorAndCohortAndIsActiveTrue(facilitator, e.getCohort()))
                .orElse(false);
    }

    @Transactional
    public UserResponse updateByAdmin(UUID targetPublicId, UpdateUserRequest request, User caller) {
        User user = findInCallerInstitution(targetPublicId, caller);
        requireCanEdit(caller, user);
        String before = profileJson(user);

        String phone = blankToNull(request.getPhoneNumber());
        if (phone != null && !phone.equals(user.getPhoneNumber())) {
            userRepository.findByPhoneNumber(phone)
                    .filter(other -> !other.getId().equals(user.getId()))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("Phone number already in use: " + phone);
                    });
        }
        if (!java.util.Objects.equals(phone, user.getPhoneNumber())) {
            user.setPhoneVerified(false); // a new number must be re-verified by its owner
        }

        user.setFirstName(request.getFirstName().trim());
        user.setMiddleName(blankToNull(request.getMiddleName()));
        user.setLastName(request.getLastName().trim());
        user.setPhoneNumber(phone);
        user.setInstitutionStudentId(blankToNull(request.getInstitutionStudentId()));
        userRepository.save(user);

        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.UPDATE,
                "User", user.getPublicId(), before, profileJson(user));
        log.info("User updated by admin: targetPublicId={}, by={}", targetPublicId, caller.getPublicId());
        return toResponse(user);
    }

    // Suspends or restores the target's access to THIS institution only.
    @Transactional
    public UserResponse setActive(UUID targetPublicId, boolean active, User caller) {
        User user = findInCallerInstitution(targetPublicId, caller);
        requireCanToggle(caller, user);
        if (!active) {
            guardAgainstSelf(user, caller, "suspend");
        }
        membershipService.setActive(user, currentInstitution(user), active, caller);
        log.info("Membership {} by admin: targetPublicId={}, by={}",
                active ? "restored" : "suspended", targetPublicId, caller.getPublicId());
        return toResponse(user);
    }

    // Removes the target from THIS institution. Their account, history and other
    // institutions are untouched (full account erasure is the user's own action).
    @Transactional
    public void removeFromInstitution(UUID targetPublicId, User caller) {
        User user = findInCallerInstitution(targetPublicId, caller);
        requireCanManage(caller, user);
        guardAgainstSelf(user, caller, "remove");
        membershipService.remove(user, currentInstitution(user), caller);
    }

    // Owner-only (controller): replace someone's roles in this institution.
    @Transactional
    public UserResponse updateRoles(UUID targetPublicId, Set<Role> roles, User caller) {
        User user = findInCallerInstitution(targetPublicId, caller);
        membershipService.updateRoles(user, currentInstitution(user), roles, caller);
        return toResponse(user);
    }

    // The caller leaves the institution they're working in.
    @Transactional
    public void leaveCurrentInstitution(User caller) {
        membershipService.remove(caller, currentInstitution(caller), caller);
    }

    private static Institution currentInstitution(User member) {
        return member.membershipIn(TenantContext.institutionId())
                .map(Membership::getInstitution)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // The target must be a member of the institution the request acts in;
    // anyone else is "not found" (no hint that they exist in another tenant).
    public User findInCallerInstitution(UUID targetPublicId, User caller) {
        User user = findEntityByPublicId(targetPublicId);
        Long here = TenantContext.institutionId();
        if (here == null || !user.isMemberOf(here)) {
            throw new ResourceNotFoundException("User not found: " + targetPublicId);
        }
        return user;
    }

    private static void guardAgainstSelf(User target, User caller, String action) {
        if (target.getId().equals(caller.getId())) {
            throw new IllegalArgumentException("You can't " + action + " yourself here. Use \"Leave institution\" instead.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String profileJson(User u) {
        return "{\"firstName\":" + q(u.getFirstName()) + ",\"middleName\":" + q(u.getMiddleName())
                + ",\"lastName\":" + q(u.getLastName()) + ",\"phoneNumber\":" + q(u.getPhoneNumber())
                + ",\"institutionStudentId\":" + q(u.getInstitutionStudentId()) + "}";
    }

    private static String q(String v) {
        return v == null ? "null" : "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getFacilitators(Institution institution) {
        return membershipRepository.findMembers(institution).stream()
                .filter(m -> m.isActive() && m.hasRole(Role.FACILITATOR))
                .map(Membership::getUser)
                .filter(User::isActive)
                .map(this::toResponse)
                .toList();
    }

    public User findEntityByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + publicId));
    }

    /**
     * PRD Section 18.1 — right-to-erasure via soft-delete/anonymization,
     * never a hard delete. PII is nulled; historical records (AttendanceRecord,
     * Submission, AuditLog) referencing this user's internal id stay intact
     * for aggregate/audit integrity.
     */
    @Transactional
    public void softDeleteAndAnonymize(UUID publicId) {
        User user = findEntityByPublicId(publicId);
        if (membershipService.isSoleOwnerSomewhere(user)) {
            throw new IllegalArgumentException("You're the only super admin of an institution. "
                    + "Make someone else a super admin there before deleting your account.");
        }
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setMiddleName(null);
        user.setEmail("deleted-" + user.getId() + "@anonymized.local");
        user.setPhoneNumber(null);
        user.setPasswordHash(null);
        user.setDeleted(true);
        userRepository.save(user);
        log.info("User soft-deleted and anonymized: publicId={}", publicId);
    }

    private Set<Role> parseRoles(Set<String> roleStrings) {
        return roleStrings.stream()
                .map(String::toUpperCase)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    private UserResponse toResponse(User user) {
        UUID cohortPublicId = null;
        String cohortName = null;

        // Cohort membership is a student-only concept — Facilitators/Admins have
        // no CohortEnrollment row, so skip the lookup entirely for non-students.
        if (user.hasRole(Role.STUDENT)) {
            var enrollment = cohortEnrollmentRepository.findByStudentAndIsActiveTrue(user);
            if (enrollment.isPresent()) {
                cohortPublicId = enrollment.get().getCohort().getPublicId();
                cohortName = enrollment.get().getCohort().getName();
            }
        }

        return UserResponse.builder()
                .publicId(user.getPublicId())
                .institutionPublicId(TenantContext.institutionPublicId())
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .profileImageUrl(user.getProfileImageUrl())
                .phoneVerified(user.isPhoneVerified())
                .webAuthnCapable(user.isWebAuthnCapable())
                .roles(user.getRoles())
                .consentGiven(user.isConsentGiven())
                .createdAt(user.getCreatedAt())
                .cohortPublicId(cohortPublicId)
                .cohortName(cohortName)
                .institutionStudentId(user.getInstitutionStudentId())
                // Active here: the account is enabled AND their membership in this institution is.
                .active(user.isActive() && user.membershipIn(TenantContext.institutionId()).map(Membership::isActive).orElse(true))
                .accountPending(user.isAccountPending())
                .build();
    }
}