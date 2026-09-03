package com.dayo.capstone.cadence.cohort.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InviteCodeResponse(
        UUID publicId,
        String code,
        UUID cohortPublicId,
        LocalDateTime expiresAt,
        int maxUses,
        LocalDateTime createdAt
) {}