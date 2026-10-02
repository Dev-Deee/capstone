package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.attendance.dto.response.CohortAttendanceOverviewEntry;
import com.dayo.capstone.cadence.attendance.dto.response.StudentAttendanceSummaryEntry;
import com.dayo.capstone.cadence.attendance.service.ResumptionAttendanceRecordService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

// Institution analytics — gated by VIEW_INSTITUTION_ANALYTICS. A cohort-scoped
// grant only ever sees its own cohort; super admins see everything in their
// institution.
@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
@Tag(name = "Admin Attendance", description = "Admin-level attendance reporting across cohorts")
public class AdminAttendanceController {

    private final ResumptionAttendanceRecordService resumptionAttendanceRecordService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final CohortService cohortService;
    private final AdminPermissionService adminPermissionService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/cohorts/{cohortPublicId}/summary")
    public ResponseEntity<List<StudentAttendanceSummaryEntry>> getCohortSummary(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID cohortPublicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, institution);
            adminPermissionService.requirePermissionForCohort(caller, Permission.VIEW_INSTITUTION_ANALYTICS, cohort);
        }
        return ResponseEntity.ok(
                resumptionAttendanceRecordService.getCohortAttendanceSummary(cohortPublicId, institution));
    }

    // Without a grant this is a 403; with a cohort-scoped grant the overview
    // lists only the cohorts that grant covers.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/cohorts")
    public ResponseEntity<List<CohortAttendanceOverviewEntry>> getAllCohorts(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        Predicate<Cohort> visible = cohort -> true;
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.VIEW_INSTITUTION_ANALYTICS);
            visible = cohort -> adminPermissionService.hasPermissionForCohort(
                    caller, Permission.VIEW_INSTITUTION_ANALYTICS, cohort);
        }
        return ResponseEntity.ok(
                resumptionAttendanceRecordService.getAllCohortsAttendanceOverview(institution, visible));
    }
}
