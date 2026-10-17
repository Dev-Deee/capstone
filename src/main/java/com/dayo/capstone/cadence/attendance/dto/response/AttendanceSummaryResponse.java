package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

@Builder
public record AttendanceSummaryResponse(
        int totalDays,
        int daysPresent,
        int daysAbsent,
        int percentPresent,
        String cohortName
) {}
