package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CohortAttendanceOverviewEntry(
        UUID cohortPublicId,
        String cohortName,
        int studentCount,
        int averageRatePercent
) {}