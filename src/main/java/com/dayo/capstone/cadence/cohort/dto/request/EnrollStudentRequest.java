package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollStudentRequest(
        @NotNull UUID studentPublicId
) {}