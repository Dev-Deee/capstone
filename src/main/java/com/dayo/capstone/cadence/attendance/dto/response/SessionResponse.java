package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record SessionResponse(
        UUID publicId,
        UUID cohortPublicId,
        UUID subjectPublicId,
        UUID facilitatorPublicId,
        String sessionCode,   // only ever returned to the facilitator who opened it — see controller note
        LocalDateTime expiresAt,
        boolean isOpen
) {}