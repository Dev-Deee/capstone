package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// One class session in a student's own history. status is the record's
// PRESENT / LATE / ABSENT, "ABSENT" when no record exists, or "OPEN" while the
// session is still running and the student hasn't marked it yet.
@Builder
public record SessionAttendanceHistoryEntry(
        UUID sessionPublicId,
        LocalDate date,
        String subjectName,
        String status,
        LocalDateTime markedAt
) {}
