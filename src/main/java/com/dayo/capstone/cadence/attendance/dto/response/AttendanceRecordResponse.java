package com.dayo.capstone.cadence.attendance.dto.response;
import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AttendanceRecordResponse(
        UUID publicId,
        UUID sessionPublicId,
        UUID studentPublicId,
        AttendanceStatus status,
        VerificationMethod verificationMethod,
        LocalDateTime submittedAt
) {}