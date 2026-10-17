package com.dayo.capstone.cadence.institution.dto.response;

import lombok.Builder;

// Platform console headline numbers.
@Builder
public record PlatformOverview(
        long institutions,
        long active,
        long pending,
        long suspended,
        long newLast30Days,
        long users,
        long memberships
) {}
