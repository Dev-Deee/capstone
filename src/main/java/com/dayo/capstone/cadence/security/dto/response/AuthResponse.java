package com.dayo.capstone.cadence.security.dto.response;

import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record AuthResponse(
        UUID userPublicId,
        String accessToken,
        String refreshToken,
        Set<String> roles,
        // The institution this session acts in (null only for a platform admin
        // outside any institution) and whether the user is a platform admin.
        UUID institutionPublicId,
        String institutionName,
        String institutionSlug,
        boolean platformAdmin
) {}