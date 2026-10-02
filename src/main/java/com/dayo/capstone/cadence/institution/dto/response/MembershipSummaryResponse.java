package com.dayo.capstone.cadence.institution.dto.response;

import java.util.Set;
import java.util.UUID;

// One row of "institutions I belong to" — powers the institution switcher.
public record MembershipSummaryResponse(
        UUID institutionPublicId,
        String institutionName,
        String institutionSlug,
        Set<String> roles,
        boolean active,
        boolean current
) {}
