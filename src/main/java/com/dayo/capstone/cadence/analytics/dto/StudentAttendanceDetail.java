package com.dayo.capstone.cadence.analytics.dto;

import com.dayo.capstone.cadence.attendance.dto.response.AttendanceHistoryEntry;
import com.dayo.capstone.cadence.attendance.dto.response.SessionAttendanceHistoryEntry;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

// One student's attendance, for an admin's view of that person. Empty (cohort
// null) when the student has no active cohort in the caller's institution.
@Builder
public record StudentAttendanceDetail(
        UUID cohortPublicId,
        String cohortName,
        List<AttendanceHistoryEntry> resumption,
        List<SessionAttendanceHistoryEntry> classes
) {}
