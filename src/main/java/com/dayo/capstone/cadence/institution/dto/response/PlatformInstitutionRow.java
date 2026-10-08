package com.dayo.capstone.cadence.institution.dto.response;

import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

// One institution in the platform console. The owner is the earliest active
// super admin — who to contact about the institution.
@Builder
public record PlatformInstitutionRow(
        UUID publicId,
        String name,
        String slug,
        InstitutionStatus status,
        LocalDateTime createdAt,
        long members,
        String ownerName,
        String ownerEmail
) {}
