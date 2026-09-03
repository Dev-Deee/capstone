package com.dayo.capstone.cadence.security.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ActiveDeviceResponse(
        UUID publicId,
        String userAgent,
        String ipAddress,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt
) {}