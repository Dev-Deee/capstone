package com.dayo.capstone.cadence.cohort.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.cohort.dto.request.AssignFacilitatorRequest;
import com.dayo.capstone.cadence.cohort.dto.response.FacilitatorCohortResponse;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.cohort.service.FacilitatorCohortService;
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
@RequestMapping("/api/facilitator-cohorts")
@RequiredArgsConstructor
@Tag(name = "Facilitator Assignments", description = "PRD Section 4.4 — facilitator-to-subject-within-cohort assignment")
public class FacilitatorCohortController {

    private final FacilitatorCohortService facilitatorCohortService;
    private final InstitutionService institutionService;
    private final CohortService cohortService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    // Admin-gated — same reasoning as invite codes and cohort creation:
    // a facilitator has no authority to assign themselves or others.
    @PreAuthorize("hasRole('SUPER_ADMIN') or isAuthenticated()")
    @PostMapping
    public ResponseEntity<FacilitatorCohortResponse> assign(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody AssignFacilitatorRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            // Cohort resolved here for the permission scope check; facilitatorCohortService
            // will re-resolve it internally — intentional double-lookup since the
            // service signature takes Institution, not Cohort.
            Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                    UUID.fromString(request.getCohortPublicId()), callerInstitution);
            adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_FACILITATOR_ASSIGNMENTS, cohort);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(facilitatorCohortService.assign(request, callerInstitution));
    }

    // A facilitator viewing their own assignments — self-service, no
    // admin gate needed since it's scoped to the caller's own identity.
    @GetMapping("/me")
    public ResponseEntity<List<FacilitatorCohortResponse>> getMyAssignments(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        return ResponseEntity.ok(facilitatorCohortService.getForFacilitator(principal.userPublicId()));
    }
}