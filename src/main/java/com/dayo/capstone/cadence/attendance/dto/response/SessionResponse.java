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
        String qrToken,
        LocalDateTime expiresAt,
        boolean isOpen,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        // Filled only in the facilitator's session list (null elsewhere):
        // students actively enrolled now, those counted as attending
        // (present or late), and those still awaiting biometric confirmation.
        Integer enrolledCount,
        Integer attendedCount,
        Integer pendingBiometricCount
) {}