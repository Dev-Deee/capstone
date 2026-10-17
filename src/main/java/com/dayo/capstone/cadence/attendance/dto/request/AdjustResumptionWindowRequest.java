package com.dayo.capstone.cadence.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AdjustResumptionWindowRequest {
    @NotNull(message = "New expiry time is required")
    private LocalDateTime newExpiresAt;
}