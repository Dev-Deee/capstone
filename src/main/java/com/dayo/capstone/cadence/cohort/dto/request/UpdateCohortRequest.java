package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateCohortRequest(
        @NotBlank String name
) {}