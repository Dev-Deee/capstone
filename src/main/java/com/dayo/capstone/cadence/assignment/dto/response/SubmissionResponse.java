package com.dayo.capstone.cadence.assignment.dto.response;

import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record SubmissionResponse(
        UUID publicId,
        UUID assignmentPublicId,
        UUID studentPublicId,
        SubmissionStatus status,
        SubmissionType submissionType,
        String linkUrl,
        String fileUrl,
        LocalDateTime submittedAt,
        int resubmissionCount,
        LocalDateTime createdAt
) {}
