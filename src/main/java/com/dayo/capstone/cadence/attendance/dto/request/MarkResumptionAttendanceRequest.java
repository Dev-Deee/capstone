package com.dayo.capstone.cadence.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkResumptionAttendanceRequest {
    @NotBlank(message = "Resumption code is required")
    private String resumptionCode;
}