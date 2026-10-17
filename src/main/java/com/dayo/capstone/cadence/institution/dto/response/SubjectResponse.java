package com.dayo.capstone.cadence.institution.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SubjectResponse(
        UUID publicId,
        UUID institutionPublicId,
        String name,
        String description,
        boolean isActive
) {}