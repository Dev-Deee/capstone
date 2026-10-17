package com.dayo.capstone.cadence.analytics.dto;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// A facilitator's view of one cohort they teach: campus resumption and their
// own classes' attendance, their assignments' hand-in picture, and the
// students who most need attention. Rates are whole percentages; null means
// nothing was held.
@Builder
public record FacilitatorCohortAnalytics(
        UUID cohortPublicId,
        String cohortName,
        int days,
        InstitutionAnalyticsResponse attendance,
        int pendingExtensions,
        List<AssignmentStats> assignments,
        List<StudentStats> students
) {
    // Published assignments only. notYet = still pending and not past due.
    @Builder
    public record AssignmentStats(
            UUID publicId, String title, String subjectName, AssignmentStatus status, LocalDateTime deadline,
            int total, int onTime, int late, int reviewed, int missing, int notYet
    ) {}

    // Over the period: campus resumption and this facilitator's classes; work
    // across this facilitator's published assignments in the cohort.
    @Builder
    public record StudentStats(
            UUID publicId, String name,
            Integer resumptionRatePercent, Integer classRatePercent,
            int handedIn, int late, int missing
    ) {}
}
