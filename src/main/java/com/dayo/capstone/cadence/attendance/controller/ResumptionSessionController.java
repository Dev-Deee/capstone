package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.attendance.dto.request.AdjustResumptionWindowRequest;
import com.dayo.capstone.cadence.attendance.dto.request.ManualAttendanceOverrideRequest;
import com.dayo.capstone.cadence.attendance.dto.request.OpenResumptionSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.CurrentCodeResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRosterEntry;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionQrRouteResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionSessionResponse;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.service.ResumptionAttendanceRecordService;
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
    private final ResumptionAttendanceRecordService resumptionAttendanceRecordService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/today")
    public ResponseEntity<List<ResumptionSessionResponse>> getTodaysSessions(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
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

        return ResponseEntity.ok(resumptionSessionService.closeManually(publicId, institution, caller));
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

    // Facilitators (any cohort at their institution), super admins, and admins
    // holding MANAGE_RESUMPTION_CODES for the cohort — the same permission that
    // already governs regenerate/close/adjust-window.
    @PreAuthorize("hasRole('FACILITATOR') or hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ResumptionSessionResponse> open(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody OpenResumptionSessionRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN) && !caller.hasRole(Role.FACILITATOR)) {
            Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                    UUID.fromString(request.getCohortPublicId()), institution);
            adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_RESUMPTION_CODES, cohort);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumptionSessionService.open(request, caller, institution));
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
    // Permission check (FACILITATOR / SUPER_ADMIN / MANAGE_RESUMPTION_CODES) is enforced
    // inside the service so all lazy-association accesses stay within one transaction
    // (spring.jpa.open-in-view=false — accessing session.getCohort() outside a transaction
    // throws LazyInitializationException → 500).
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{publicId}/current-code")
    public ResponseEntity<CurrentCodeResponse> getCurrentCode(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionSessionService.getCurrentCode(publicId, institution, caller));
    }

    // All resumption sessions for a cohort, newest first — attendance history picker.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ResumptionSessionResponse>> listByCohort(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestParam UUID cohortPublicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        requireAttendanceRead(caller, cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, institution));
        return ResponseEntity.ok(resumptionSessionService.getSessionsByCohort(cohortPublicId, institution));
    }

    // Attendance roster — present/absent per enrolled student for this resumption session.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/{publicId}/attendance")
    public ResponseEntity<List<ResumptionAttendanceRosterEntry>> getAttendanceRoster(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        requireAttendanceRead(caller,
                resumptionSessionService.findEntityByPublicIdScopedToInstitution(publicId, institution).getCohort());
        return ResponseEntity.ok(resumptionSessionService.getAttendanceRoster(publicId, institution));
    }

    // PRD Section 6.6 — manual override (PRESENT / LATE / ABSENT) for one student.
    // Same access rule as the roster above; MANAGE_RESUMPTION_CODES checked in the service.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PutMapping("/{publicId}/attendance/{studentPublicId}")
    public ResponseEntity<ResumptionAttendanceRecordResponse> overrideAttendance(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @PathVariable UUID studentPublicId,
            @Valid @RequestBody ManualAttendanceOverrideRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionAttendanceRecordService.manualOverride(
                publicId, studentPublicId, request, caller, institution));
    }

    // Public (unauthenticated) — resolves a QR token to enough info for a
    // student to confirm they scanned the right resumption session before
    // typing the spoken code. Never returns the resumption code itself.
    @GetMapping("/qr/{qrToken}")
    public ResponseEntity<ResumptionSessionQrRouteResponse> getByQrToken(@PathVariable String qrToken) {
        return ResponseEntity.ok(resumptionSessionService.getQrRouteInfo(qrToken));
    }

    // Reading a cohort's resumption record: analysts (VIEW_INSTITUTION_ANALYTICS)
    // and the admins who run resumption for it (MANAGE_RESUMPTION_CODES, who
    // need the roster to make overrides).
    private void requireAttendanceRead(User caller, Cohort cohort) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return;
        if (adminPermissionService.hasPermissionForCohort(caller, Permission.VIEW_INSTITUTION_ANALYTICS, cohort)) return;
        adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_RESUMPTION_CODES, cohort);
    }
}