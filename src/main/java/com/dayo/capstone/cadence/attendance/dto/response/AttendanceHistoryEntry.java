package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AttendanceHistoryEntry(
        UUID sessionPublicId,
        LocalDate date,
        String status,          // "PRESENT" or "ABSENT"
        LocalDateTime checkedInAt
) {}
