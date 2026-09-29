package com.dayo.capstone.cadence.admin.service;

import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.enums.*;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final UserAdminRoleAssignmentRepository assignmentRepository;


    @Transactional(readOnly = true)
    public boolean hasPermission(User user, Permission permission) {
        return assignmentRepository.findByUserAndIsActiveTrue(user).stream()
                .filter(a -> a.getAdminRole().isActive())
                .anyMatch(a -> a.getAdminRole().getPermissions().contains(permission));
    }

    @Transactional(readOnly = true)
    public boolean hasPermissionForCohort(User user, Permission permission, Cohort cohort) {
        return assignmentRepository.findByUserAndIsActiveTrue(user).stream()
                .filter(a -> a.getAdminRole().isActive())
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
}