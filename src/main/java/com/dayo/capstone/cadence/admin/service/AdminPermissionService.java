package com.dayo.capstone.cadence.admin.service;

import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.enums.*;
import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Granular admin permissions (AdminRole + UserAdminRoleAssignment).
 *
 * Multi-tenant: only grants from the institution the current request acts in
 * count. A user can hold admin roles in several institutions; a MANAGE_COHORTS
 * grant in institution A must never apply while they work in institution B —
 * including an institution-wide grant (assignment.cohort == null), which covers
 * every cohort of ITS institution only.
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final UserAdminRoleAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public boolean hasPermission(User user, Permission permission) {
        return activeGrantsInCurrentInstitution(user).stream()
                .anyMatch(a -> a.getAdminRole().getPermissions().contains(permission));
    }

    @Transactional(readOnly = true)
    public boolean hasPermissionForCohort(User user, Permission permission, Cohort cohort) {
        return activeGrantsInCurrentInstitution(user).stream()
                .filter(a -> a.getAdminRole().getPermissions().contains(permission))
                .anyMatch(a -> a.getCohort() == null || a.getCohort().getId().equals(cohort.getId()));
    }

    @Transactional(readOnly = true)
    public void requirePermission(User user, Permission permission) {
        if (!hasPermission(user, permission)) {
            throw new AccessDeniedException("Missing required permission: " + permission);
        }
    }

    @Transactional(readOnly = true)
    public void requirePermissionForCohort(User user, Permission permission, Cohort cohort) {
        if (!hasPermissionForCohort(user, permission, cohort)) {
            throw new AccessDeniedException("Missing required permission: " + permission + " for this cohort");
        }
    }

    /** The user's active, active-role grants whose AdminRole belongs to the institution in context. */
    @Transactional(readOnly = true)
    public List<UserAdminRoleAssignment> activeGrantsInCurrentInstitution(User user) {
        Long institutionId = TenantContext.institutionId();
        if (institutionId == null) return List.of();
        return assignmentRepository.findByUserAndIsActiveTrue(user).stream()
                .filter(a -> a.getAdminRole().isActive())
                .filter(a -> a.getAdminRole().getInstitution().getId().equals(institutionId))
                .toList();
    }
}
