package com.dayo.capstone.cadence.institution.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.dto.request.CreateSubjectRequest;
import com.dayo.capstone.cadence.institution.dto.response.SubjectResponse;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.institution.service.SubjectService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@Tag(name = "Subjects", description = "PRD Section 4.4 — subjects taught within a cohort")
public class SubjectController {

    private final SubjectService subjectService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    // Admin-gated, same boundary as Cohort creation — a facilitator
    // doesn't get to invent new subjects unilaterally.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<SubjectResponse> create(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody CreateSubjectRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_SUBJECTS);
        }
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.create(request, institution));
    }

    @GetMapping
    public ResponseEntity<List<SubjectResponse>> getAll() {
        return ResponseEntity.ok(subjectService.getAll());
    }
}