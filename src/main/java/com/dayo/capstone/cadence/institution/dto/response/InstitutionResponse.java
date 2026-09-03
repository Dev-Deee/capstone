package com.dayo.capstone.cadence.institution.dto.response;

import com.dayo.capstone.cadence.institution.enums.GuardianLinkageMode;
import com.dayo.capstone.cadence.institution.enums.GuardianNotificationDefault;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What the API actually returns. Notice: no internal `id` field exists
 * here at all — `publicId` is the only identifier the outside world ever sees.
 */
@Builder
public record InstitutionResponse(
        UUID publicId,
        String name,
        String address,
        boolean isActive,
        GuardianLinkageMode guardianLinkageMode,
        GuardianNotificationDefault guardianNotificationDefault,
        LocalDateTime createdAt
) {}