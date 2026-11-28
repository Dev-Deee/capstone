package com.dayo.capstone.cadence.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AnnouncementResponse(
        UUID publicId,
        String title,
        String body,
        String authorName,
        UUID authorPublicId,
        List<CohortRef> cohorts,
        LocalDateTime createdAt,
        boolean canDelete
) {
    public record CohortRef(UUID publicId, String name) {}
}
