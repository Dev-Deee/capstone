package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ResumptionAttendanceRosterEntry(
        UUID studentPublicId,
        String studentName,
        String status,
        LocalDateTime markedAt,
        Boolean matchedInstitutionNetwork,
        Boolean webAuthnVerified,
        Integer confidenceScore
) {}