package com.dayo.capstone.cadence.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenResumptionSessionRequest {
    @NotBlank(message = "Cohort public ID is required")
    private String cohortPublicId;

    @NotNull(message = "Expiry duration in minutes is required")
    private Integer expiryMinutes;
}