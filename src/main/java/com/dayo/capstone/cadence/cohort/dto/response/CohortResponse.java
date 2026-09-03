package com.dayo.capstone.cadence.cohort.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CohortResponse(
        UUID publicId,
        UUID institutionPublicId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        boolean isActive
) {}