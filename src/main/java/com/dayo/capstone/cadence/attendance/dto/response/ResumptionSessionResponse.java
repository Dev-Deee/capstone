package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ResumptionSessionResponse(
        UUID publicId,
        UUID cohortPublicId,
        String resumptionCode,
        String qrToken,
        LocalDateTime opensAt,
        LocalDateTime expiresAt,
        boolean isOpen
) {}