package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.request.MarkResumptionAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceHistoryEntry;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceSummaryResponse;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.service.ResumptionAttendanceRecordService;
import com.dayo.capstone.cadence.attendance.dto.response.BiometricChallenge;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.security.dto.request.WebAuthnAuthenticationRequest;
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
@RequestMapping("/api/resumption-attendance")
@RequiredArgsConstructor
@Tag(name = "Resumption Attendance", description = "Student check-in against Tier 1 campus resumption codes")
public class ResumptionAttendanceRecordController {

    private final ResumptionAttendanceRecordService resumptionAttendanceRecordService;
    private final UserService userService;

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<ResumptionAttendanceRecordResponse> markAttendance(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody MarkResumptionAttendanceRequest request
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumptionAttendanceRecordService.markAttendance(request, student));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me/summary")
    public ResponseEntity<AttendanceSummaryResponse> getMySummary(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionAttendanceRecordService.getAttendanceSummary(student));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me/history")
    public ResponseEntity<List<AttendanceHistoryEntry>> getMyHistory(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionAttendanceRecordService.getAttendanceHistory(student));
    }

    // PRD Section 6.12 — the biometric step after the code was accepted.
    // A fresh challenge for a pending resumption check-in (retry after a cancelled
    // prompt or a dropped connection — the code isn't asked for again).
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{recordPublicId}/biometric/challenge")
    public ResponseEntity<BiometricChallenge> biometricChallenge(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID recordPublicId
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionAttendanceRecordService.reissueBiometricChallenge(recordPublicId, student));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{recordPublicId}/biometric")
    public ResponseEntity<ResumptionAttendanceRecordResponse> confirmBiometric(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID recordPublicId,
            @RequestBody WebAuthnAuthenticationRequest assertion
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(resumptionAttendanceRecordService.confirmBiometric(recordPublicId, assertion, student));
    }
}