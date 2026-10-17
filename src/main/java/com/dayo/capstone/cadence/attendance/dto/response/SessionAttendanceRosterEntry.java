package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record SessionAttendanceRosterEntry(
        UUID studentPublicId,
        String studentName,
        String status,
        LocalDateTime markedAt,
        String verificationMethod
) {}