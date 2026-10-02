package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.response.SessionAttendanceHistoryEntry;
import com.dayo.capstone.cadence.attendance.service.AttendanceRecordService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Student Attendance", description = "A student's own class-session attendance history")
public class StudentAttendanceController {

    private final AttendanceRecordService attendanceRecordService;
    private final UserService userService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me/session-attendance-history")
    public ResponseEntity<List<SessionAttendanceHistoryEntry>> getMySessionHistory(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(attendanceRecordService.getSessionHistory(student));
    }
}
