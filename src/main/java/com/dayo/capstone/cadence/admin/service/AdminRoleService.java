package com.dayo.capstone.cadence.admin.service;

import com.dayo.capstone.cadence.admin.dto.request.AssignAdminRoleRequest;
import com.dayo.capstone.cadence.admin.dto.request.CreateAdminRoleRequest;
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
    public void assign(AssignAdminRoleRequest request) {
        User user = userService.findEntityByPublicId(UUID.fromString(request.getUserPublicId()));
        AdminRole role = adminRoleRepository.findByPublicId(UUID.fromString(request.getAdminRolePublicId()))
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole not found"));

        Cohort cohort = request.getCohortPublicId() != null
                ? cohortService.findEntityByPublicId(UUID.fromString(request.getCohortPublicId()))
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