package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.attendance.dto.request.AdjustResumptionWindowRequest;
import com.dayo.capstone.cadence.attendance.dto.request.OpenResumptionSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.CurrentCodeResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionQrRouteResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionResponse;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.service.ResumptionSessionService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.service.CohortService;
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
@RequestMapping("/api/resumption-sessions")
@RequiredArgsConstructor
@Tag(name = "Resumption Sessions", description = "Tier 1 campus attendance — admin-managed daily codes per cohort")
public class ResumptionSessionController {

    private final ResumptionSessionService resumptionSessionService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;
    private final CohortService cohortService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or isAuthenticated()")
    @GetMapping("/today")
    public ResponseEntity<List<ResumptionSessionResponse>> getTodaysSessions(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());

        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_RESUMPTION_CODES);
        }

        return ResponseEntity.ok(resumptionSessionService.getTodaysSessions(institution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or isAuthenticated()")
    @PatchMapping("/{publicId}/regenerate")
    public ResponseEntity<ResumptionSessionResponse> regenerateCode(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        ResumptionSession session = resumptionSessionService.findEntityByPublicId(publicId);

        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_RESUMPTION_CODES, session.getCohort());
        }

        return ResponseEntity.ok(resumptionSessionService.regenerateCode(publicId, institution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or isAuthenticated()")
    @PatchMapping("/{publicId}/close")
    public ResponseEntity<ResumptionSessionResponse> closeManually(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        ResumptionSession session = resumptionSessionService.findEntityByPublicId(publicId);

        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_RESUMPTION_CODES, session.getCohort());
        }

        return ResponseEntity.ok(resumptionSessionService.closeManually(publicId, institution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or isAuthenticated()")
    @PatchMapping("/{publicId}/adjust-window")
    public ResponseEntity<ResumptionSessionResponse> adjustWindow(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @Valid @RequestBody AdjustResumptionWindowRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        ResumptionSession session = resumptionSessionService.findEntityByPublicId(publicId);

        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_RESUMPTION_CODES, session.getCohort());
        }

        return ResponseEntity.ok(
                resumptionSessionService.adjustWindow(publicId, request.getNewExpiresAt(), institution));
    }

    @PreAuthorize("hasRole('FACILITATOR')")
    @PostMapping
    public ResponseEntity<ResumptionSessionResponse> open(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody OpenResumptionSessionRequest request
    ) {
        User facilitator = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumptionSessionService.open(request, facilitator, institution));
    }

    @PreAuthorize("hasRole('FACILITATOR')")
    @GetMapping("/today/mine")
    public ResponseEntity<List<ResumptionSessionResponse>> getTodayForFacilitator(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionSessionService.getTodayForFacilitator(institution));
    }

    // Returns the live rotating TOTP code and seconds until rotation.
    // Requires MANAGE_RESUMPTION_CODES permission (or SUPER_ADMIN) — same
    // ownership check as the other mutating endpoints on this resource.
    @PreAuthorize("hasRole('SUPER_ADMIN') or isAuthenticated()")
    @GetMapping("/{publicId}/current-code")
    public ResponseEntity<CurrentCodeResponse> getCurrentCode(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        ResumptionSession session = resumptionSessionService.findEntityByPublicId(publicId);

        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(
                    caller, Permission.MANAGE_RESUMPTION_CODES, session.getCohort());
        }

        return ResponseEntity.ok(resumptionSessionService.getCurrentCode(publicId, institution));
    }

    // Public (unauthenticated) — resolves a QR token to enough info for a
    // student to confirm they scanned the right resumption session before
    // typing the spoken code. Never returns the resumption code itself.
    @GetMapping("/qr/{qrToken}")
    public ResponseEntity<ResumptionSessionQrRouteResponse> getByQrToken(@PathVariable String qrToken) {
        return ResponseEntity.ok(resumptionSessionService.getQrRouteInfo(qrToken));
    }
}