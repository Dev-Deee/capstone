package com.dayo.capstone.cadence.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkAttendanceRequest {
    @NotBlank(message = "Session code is required")
    private String sessionCode;
}