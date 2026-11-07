package com.dayo.capstone.cadence.assignment.dto.response;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.enums.GradingType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AssignmentResponse(
        UUID publicId,
        UUID cohortPublicId,
        UUID subjectPublicId,
        UUID facilitatorPublicId,
        String title,
        String description,
        AssignmentStatus status,
        LocalDateTime deadline,
        int maxResubmissions,
        boolean isLocked,
        String clarificationNote,
        LocalDateTime createdAt,
        String attachmentUrl,
        String attachmentName,
        LocalDateTime publishAt,
        GradingType gradingType,
        Integer maxMarks,
        LocalDateTime gradesReleasedAt
) {}
