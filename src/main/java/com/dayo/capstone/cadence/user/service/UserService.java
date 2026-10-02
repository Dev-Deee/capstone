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
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
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

    // Scoped to the caller's institution — the directory never crosses tenants.
    @Transactional(readOnly = true)
    public List<UserResponse> getAll(Institution institution) {
        return userRepository.findByInstitutionAndIsDeletedFalseOrderByCreatedAtDesc(institution).stream()
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
    // an AuditLog entry. Deactivate/delete refuse to act on the caller themselves
    // or on SUPER_ADMIN accounts, so nobody can lock the institution out.

    // Edit, email change and delete — admin tiers only.
    public void requireCanEdit(User caller, User target) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return;
        if (isUserAdmin(caller)) {
            requireNotStaffAdmin(target);
            return;
        }
        throw new AccessDeniedException("You don't have permission to manage this user");
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

    @Transactional
    public UserResponse setActive(UUID targetPublicId, boolean active, User caller) {
        User user = findInCallerInstitution(targetPublicId, caller);
        requireCanToggle(caller, user);
        if (!active) {
            guardAgainstLockout(user, caller, "deactivate");
        }
        if (user.isActive() == active) {
            return toResponse(user);
        }

        user.setActive(active);
        userRepository.save(user);
        if (!active) {
            refreshTokenRepository.revokeAllForUser(user, LocalDateTime.now());
        }

        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.UPDATE,
                "User", user.getPublicId(),
                "{\"active\":" + !active + "}", "{\"active\":" + active + "}");
        log.info("User {} by admin: targetPublicId={}, by={}",
                active ? "activated" : "deactivated", targetPublicId, caller.getPublicId());
        return toResponse(user);
    }

    // Soft delete only (PRD 18.1): anonymize PII, sign out everywhere, and end
    // any active cohort enrollment so the user drops out of live statistics.
    // Historical attendance/submissions/audit rows are kept.
    @Transactional
    public void softDeleteByAdmin(UUID targetPublicId, User caller) {
        User user = findInCallerInstitution(targetPublicId, caller);
        requireCanEdit(caller, user);
        guardAgainstLockout(user, caller, "delete");

        refreshTokenRepository.revokeAllForUser(user, LocalDateTime.now());
        cohortEnrollmentRepository.findByStudentAndIsActiveTrue(user).ifPresent(e -> {
            e.setActive(false);
            cohortEnrollmentRepository.save(e);
        });
        softDeleteAndAnonymize(targetPublicId);

        auditLogService.log(caller.getPublicId(), caller.getRoles().toString(), AuditAction.DELETE,
                "User", user.getPublicId());
        log.info("User soft-deleted by admin: targetPublicId={}, by={}", targetPublicId, caller.getPublicId());
    }

    public User findInCallerInstitution(UUID targetPublicId, User caller) {
        User user = findEntityByPublicId(targetPublicId);
        if (!user.getInstitution().getId().equals(caller.getInstitution().getId())) {
            throw new ResourceNotFoundException("User not found: " + targetPublicId);
        }
        return user;
    }

    private static void guardAgainstLockout(User target, User caller, String action) {
        if (target.getId().equals(caller.getId())) {
            throw new IllegalArgumentException("You can't " + action + " your own account");
        }
        if (target.hasRole(Role.SUPER_ADMIN)) {
            throw new IllegalArgumentException("Super admin accounts can't be " + action + "d here");
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
        return userRepository.findByInstitutionAndIsDeletedFalseOrderByCreatedAtDesc(institution).stream()
                .filter(User::isActive)
                .filter(u -> u.hasRole(Role.FACILITATOR))
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
                .institutionPublicId(user.getInstitution().getPublicId())
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
                .active(user.isActive())
                .accountPending(user.isAccountPending())
                .build();
    }
}