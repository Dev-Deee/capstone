package com.dayo.capstone.cadence.institution.dto.response;

import java.util.Set;
import java.util.UUID;

public record MembershipSummaryResponse(
        UUID institutionPublicId,
        String institutionName,
        String institutionHandle,
        Set<String> roles,
        boolean active,
        boolean current
) {}
