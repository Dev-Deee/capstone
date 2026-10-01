package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record StudentAttendanceSummaryEntry(
        UUID studentPublicId,
        String studentName,
        int totalDays,
        int present,
        int absent,
        int ratePercent
) {}