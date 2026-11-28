package com.dayo.capstone.cadence.assignment.dto.response;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

// One assignment in the institution-wide review list a SUPER_ADMIN sees:
// where it lives, who set it, and how much handed-in work is waiting on it.
@Builder
public record AssignmentOverviewRow(
        UUID publicId,
        String title,
        AssignmentStatus status,
        LocalDateTime deadline,
        LocalDateTime publishAt,
        UUID cohortPublicId,
        String cohortName,
        UUID subjectPublicId,
        String subjectName,
        UUID facilitatorPublicId,
        String facilitatorName,
        long waiting,
        LocalDateTime gradesReleasedAt
) {}
