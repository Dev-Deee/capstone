package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.response.CohortAttendanceOverviewEntry;
import com.dayo.capstone.cadence.attendance.dto.response.StudentAttendanceSummaryEntry;
import com.dayo.capstone.cadence.attendance.service.ResumptionAttendanceRecordService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
@Tag(name = "Admin Attendance", description = "Admin-level attendance reporting across cohorts")
public class AdminAttendanceController {

    private final ResumptionAttendanceRecordService resumptionAttendanceRecordService;
    private final InstitutionService institutionService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/cohorts/{cohortPublicId}/summary")
    public ResponseEntity<List<StudentAttendanceSummaryEntry>> getCohortSummary(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID cohortPublicId
    ) {
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(
                resumptionAttendanceRecordService.getCohortAttendanceSummary(cohortPublicId, institution));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/cohorts")
    public ResponseEntity<List<CohortAttendanceOverviewEntry>> getAllCohorts(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(
                resumptionAttendanceRecordService.getAllCohortsAttendanceOverview(institution));
    }
}