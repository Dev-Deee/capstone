package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.request.MarkAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.service.AttendanceRecordService;
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
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "PRD Section 6 — attendance marking, session-code verification")
public class AttendanceRecordController {

    private final AttendanceRecordService attendanceRecordService;
    private final UserService userService;

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<AttendanceRecordResponse> markAttendance(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody MarkAttendanceRequest request
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceRecordService.markAttendance(request, student));
    }
}