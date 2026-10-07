package com.dayo.capstone.cadence.admin.service;

import com.dayo.capstone.cadence.admin.dto.request.AssignAdminRoleRequest;
import com.dayo.capstone.cadence.admin.dto.request.CreateAdminRoleRequest;
import com.dayo.capstone.cadence.admin.dto.request.UpdateAdminRoleRequest;
import com.dayo.capstone.cadence.admin.dto.response.AdminRoleResponse;
import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.admin.repository.AdminRoleRepository;
import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;
    private final UserAdminRoleAssignmentRepository assignmentRepository;
    private final UserService userService;
    private final CohortService cohortService;

    @Transactional
    public AdminRoleResponse create(CreateAdminRoleRequest request, Institution institution) {
        AdminRole role = new AdminRole();
        role.setInstitution(institution);
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions().stream()
                .map(String::toUpperCase)
                .map(Permission::valueOf)
                .collect(Collectors.toSet()));

        AdminRole saved = adminRoleRepository.save(role);
        log.info("AdminRole created: publicId={}, name={}", saved.getPublicId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public AdminRoleResponse update(UUID adminRolePublicId, UpdateAdminRoleRequest request, Institution callerInstitution) {
        AdminRole role = adminRoleRepository.findByPublicId(adminRolePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole not found: " + adminRolePublicId));

        if (!role.getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("AdminRole not found: " + adminRolePublicId);
        }

        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getPermissions() != null) {
            role.setPermissions(request.getPermissions().stream()
                    .map(String::toUpperCase)
                    .map(Permission::valueOf)
                    .collect(Collectors.toSet()));
        }
        if (request.getIsActive() != null) {
            role.setActive(request.getIsActive());
        }

        AdminRole saved = adminRoleRepository.save(role);
        log.info("AdminRole updated: publicId={}", saved.getPublicId());
        return toResponse(saved);
    }

    @Transactional
    public void assign(AssignAdminRoleRequest request, Institution callerInstitution) {
        User user = userService.findEntityByPublicId(UUID.fromString(request.getUserPublicId()));
        if (!user.isMemberOf(callerInstitution.getId())) {
            throw new ResourceNotFoundException("User not found");
        }
        AdminRole role = adminRoleRepository.findByPublicId(UUID.fromString(request.getAdminRolePublicId()))
                .filter(r -> r.getInstitution().getId().equals(callerInstitution.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole not found"));

        Cohort cohort = request.getCohortPublicId() != null
                ? cohortService.findEntityByPublicIdScopedToInstitution(UUID.fromString(request.getCohortPublicId()), callerInstitution)
                : null;

        UserAdminRoleAssignment assignment = new UserAdminRoleAssignment();
        assignment.setUser(user);
        assignment.setAdminRole(role);
        assignment.setCohort(cohort);
        assignment.setAssignedAt(LocalDateTime.now());

        assignmentRepository.save(assignment);
        log.info("AdminRole assigned: userPublicId={}, rolePublicId={}, cohortPublicId={}",
                user.getPublicId(), role.getPublicId(), cohort != null ? cohort.getPublicId() : "institution-wide");
    }

    public List<AdminRoleResponse> getAll(Institution institution) {
        return adminRoleRepository.findByInstitution(institution).stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminRoleResponse toResponse(AdminRole role) {
        return AdminRoleResponse.builder()
                .publicId(role.getPublicId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions())
                .isActive(role.isActive())
                .build();
    }
}