package com.dayo.capstone.cadence.cohort.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record FacilitatorCohortResponse(
        UUID publicId,
        UUID facilitatorPublicId,
        UUID cohortPublicId,
        UUID subjectPublicId,
        LocalDateTime assignedAt,
        boolean isActive
) {}