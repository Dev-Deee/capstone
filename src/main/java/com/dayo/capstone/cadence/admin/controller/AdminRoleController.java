package com.dayo.capstone.cadence.admin.controller;

import com.dayo.capstone.cadence.admin.dto.request.AssignAdminRoleRequest;
import com.dayo.capstone.cadence.admin.dto.request.CreateAdminRoleRequest;
import com.dayo.capstone.cadence.admin.dto.response.AdminRoleResponse;
import com.dayo.capstone.cadence.admin.service.AdminRoleService;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin-roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;
    private final InstitutionService institutionService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<AdminRoleResponse> create(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody CreateAdminRoleRequest request
    ) {
        var institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRoleService.create(request, institution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/assign")
    public ResponseEntity<Void> assign(@Valid @RequestBody AssignAdminRoleRequest request) {
        adminRoleService.assign(request);
        return ResponseEntity.noContent().build();
    }
}