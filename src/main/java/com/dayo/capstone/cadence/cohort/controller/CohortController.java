package com.dayo.capstone.cadence.cohort.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.cohort.dto.request.CreateCohortRequest;
import com.dayo.capstone.cadence.cohort.dto.response.CohortResponse;
import com.dayo.capstone.cadence.cohort.dto.request.CreateInviteCodeRequest;
import com.dayo.capstone.cadence.cohort.dto.response.InviteCodeResponse;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.cohort.service.CohortInviteCodeService;
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
@RequestMapping("/api/cohorts")
@RequiredArgsConstructor
@Tag(name = "Cohorts", description = "PRD Section 4 — cohorts within an institution")
public class CohortController {

    private final CohortService cohortService;
    private final CohortInviteCodeService inviteCodeService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<CohortResponse> create(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody CreateCohortRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_COHORTS);
        }
        var institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED).body(cohortService.create(request, institution));
    }

    // Nested under a specific cohort's own path, and given its own method
    // name — resolves both the duplicate-mapping and duplicate-method-name
    // problems from the merged version.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCodeResponse> createInviteCode(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody CreateInviteCodeRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution callerInstitution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            // Cohort resolved here for the permission scope check; inviteCodeService
            // will re-resolve it internally — intentional double-lookup since the
            // service signature takes Institution, not Cohort.
            Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                    UUID.fromString(request.getCohortPublicId()), callerInstitution);
            adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_INVITE_CODES, cohort);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inviteCodeService.create(request, callerInstitution));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<CohortResponse> getOne(@PathVariable UUID publicId) {
        return ResponseEntity.ok(cohortService.getByPublicId(publicId));
    }

    @GetMapping
    public ResponseEntity<List<CohortResponse>> getAll() {
        return ResponseEntity.ok(cohortService.getAll());
    }
}
