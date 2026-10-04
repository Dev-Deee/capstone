package com.dayo.capstone.cadence.analytics.controller;

import com.dayo.capstone.cadence.analytics.dto.FacilitatorCohortAnalytics;
import com.dayo.capstone.cadence.analytics.service.FacilitatorAnalyticsService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// A facilitator's analytics for one cohort they teach (any active class in it).
@RestController
@RequestMapping("/api/facilitator/analytics")
@RequiredArgsConstructor
@Tag(name = "Facilitator Analytics", description = "Resumption, class attendance and assignments for a cohort you teach")
public class FacilitatorAnalyticsController {

    private final FacilitatorAnalyticsService analyticsService;
    private final CohortService cohortService;
    private final InstitutionService institutionService;
    private final UserService userService;

    @PreAuthorize("hasRole('FACILITATOR')")
    @GetMapping
    public ResponseEntity<FacilitatorCohortAnalytics> get(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestParam UUID cohortPublicId,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (days < 7 || days > 90) {
            throw new IllegalArgumentException("days must be between 7 and 90");
        }
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, institution);
        return ResponseEntity.ok(analyticsService.compute(caller, cohort, institution, days));
    }
}
