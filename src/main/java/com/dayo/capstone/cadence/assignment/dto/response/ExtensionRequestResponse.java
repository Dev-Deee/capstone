package com.dayo.capstone.cadence.assignment.dto.response;

import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ExtensionRequestResponse(
        UUID publicId,
        UUID submissionPublicId,
        UUID studentPublicId,
        String studentName,
        String assignmentTitle,
        LocalDateTime requestedNewDeadline,
        String reason,
        ExtensionRequestStatus status,
        UUID reviewedByPublicId,
        String reviewNote,
        LocalDateTime createdAt
) {}
