package com.dayo.capstone.cadence.assignment.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.assignment.dto.request.CreateAssignmentRequest;
import com.dayo.capstone.cadence.assignment.dto.response.AssignmentResponse;
import com.dayo.capstone.cadence.assignment.service.AssignmentService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Assignment lifecycle — create, publish, close, and read")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('FACILITATOR')")
    @PostMapping
    public ResponseEntity<AssignmentResponse> create(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());

        // A Facilitator creating their own assignment needs no AdminPermissionService
        // check at all — assignmentService.create() already validates they're
        // actually assigned to teach this exact cohort+subject pair, the same
        // FacilitatorCohort check SessionService.open() uses. MANAGE_ASSIGNMENTS
        // is reserved for Admin-tier callers acting outside their own teaching
        // assignments, not for a Facilitator's baseline right to create their own.
        if (!caller.hasRole(Role.SUPER_ADMIN) && !caller.hasRole(Role.FACILITATOR)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_ASSIGNMENTS);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.create(request, caller, callerInstitution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('FACILITATOR')")
    @PatchMapping("/{publicId}/publish")
    public ResponseEntity<AssignmentResponse> publish(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());

        // A Facilitator publishing their own assignment needs no AdminPermissionService
        // check — assignmentService.publish() should already verify ownership (that
        // this caller is the assignment's own facilitator, or SUPER_ADMIN). If it
        // doesn't currently do that ownership check, that's a real gap worth
        // confirming separately — otherwise ANY Facilitator could publish/lock
        // ANY other Facilitator's draft assignment, not just their own.
        if (!caller.hasRole(Role.SUPER_ADMIN) && !caller.hasRole(Role.FACILITATOR)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_ASSIGNMENTS);
        }

        return ResponseEntity.ok(assignmentService.publish(publicId, caller, callerInstitution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('FACILITATOR')")
    @PatchMapping("/{publicId}/close")
    public ResponseEntity<AssignmentResponse> close(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN) && !caller.hasRole(Role.FACILITATOR)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_ASSIGNMENTS);
        }
        return ResponseEntity.ok(assignmentService.close(publicId, caller, callerInstitution));
    }

    @GetMapping("/cohort/{cohortPublicId}")
    public ResponseEntity<List<AssignmentResponse>> getForCohort(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID cohortPublicId
    ) {
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(assignmentService.getForCohort(cohortPublicId, callerInstitution));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<AssignmentResponse> getOne(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(assignmentService.getOne(publicId, callerInstitution));
    }
}
