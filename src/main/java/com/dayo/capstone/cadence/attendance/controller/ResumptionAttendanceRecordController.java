package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.request.MarkResumptionAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.service.ResumptionAttendanceRecordService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
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
}