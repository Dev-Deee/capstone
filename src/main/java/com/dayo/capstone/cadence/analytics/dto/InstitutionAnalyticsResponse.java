package com.dayo.capstone.cadence.analytics.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

// The analytics dashboard in one payload, over the cohorts the caller may see.
// Rates are whole percentages; a null rate means nothing was scheduled.
@Builder
public record InstitutionAnalyticsResponse(
        int days,
        int cohortCount,
        int studentCount,
        // Campus resumption over the period: attended / expected check-ins.
        Integer resumptionRatePercent,
        // Class sessions over the period: attended / expected.
        Integer classRatePercent,
        Today today,
        // Campus resumption check-ins over the period, by outcome.
        Split split,
        List<TrendPoint> trend
) {
    @Builder
    public record Today(int expected, int present, int late, int pendingBiometric, int openSessions) {}

    // absent = expected − present − late − pendingBiometric (no record, or an ABSENT override).
    @Builder
    public record Split(int present, int late, int absent, int pendingBiometric) {}

    @Builder
    public record TrendPoint(LocalDate date, Integer resumptionRatePercent, Integer classRatePercent) {}
}
