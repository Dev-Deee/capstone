
package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ResumptionAttendanceRecordResponse(
        UUID publicId,
        UUID resumptionSessionPublicId,
        UUID studentPublicId,
        LocalDateTime checkedInAt,
        String status,
        Boolean webAuthnVerified,
        // Present only while status is PENDING_BIOMETRIC.
        BiometricChallenge biometric
) {}