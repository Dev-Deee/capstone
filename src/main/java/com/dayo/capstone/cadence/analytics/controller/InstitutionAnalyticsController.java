package com.dayo.capstone.cadence.analytics.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.analytics.dto.InstitutionAnalyticsResponse;
import com.dayo.capstone.cadence.analytics.service.InstitutionAnalyticsService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
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

import java.util.function.Predicate;

// The analytics dashboard. Same rule as the admin attendance reports:
// VIEW_INSTITUTION_ANALYTICS (super admins exempt); a cohort-scoped grant
// only ever counts its own cohorts.
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Institution Analytics", description = "Attendance analytics across the caller's visible cohorts")
public class InstitutionAnalyticsController {

    private final InstitutionAnalyticsService analyticsService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<InstitutionAnalyticsResponse> get(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (days < 7 || days > 90) {
            throw new IllegalArgumentException("days must be between 7 and 90");
        }
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        Predicate<Cohort> visible = cohort -> true;
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.VIEW_INSTITUTION_ANALYTICS);
            visible = cohort -> adminPermissionService.hasPermissionForCohort(
                    caller, Permission.VIEW_INSTITUTION_ANALYTICS, cohort);
        }
        return ResponseEntity.ok(analyticsService.compute(institution, visible, days));
    }
}
