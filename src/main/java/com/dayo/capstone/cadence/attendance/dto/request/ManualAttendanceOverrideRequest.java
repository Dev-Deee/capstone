package com.dayo.capstone.cadence.attendance.dto.request;

import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// PRD Section 6.6 — a manual override always carries a reason.
@Getter
@Setter
public class ManualAttendanceOverrideRequest {
    @NotNull(message = "Status is required (PRESENT, LATE or ABSENT)")
    private AttendanceStatus status;

    @NotBlank(message = "A reason is required for a manual override")
    private String reason;
}
