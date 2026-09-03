package com.dayo.capstone.cadence.security.dto.response;

import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record AuthResponse(
        UUID userPublicId,
        String accessToken,
        String refreshToken,
        Set<String> roles
) {}