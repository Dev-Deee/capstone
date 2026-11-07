package com.dayo.capstone.cadence.assignment.dto.response;

import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.assignment.enums.GradingType;
import lombok.Builder;

import java.math.BigDecimal;
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
        LocalDateTime createdAt,
        // The deadline that actually applies: an approved extension's, else the assignment's.
        LocalDateTime effectiveDeadline,
        // The latest extension request's status, or null if none was made.
        ExtensionRequestStatus extensionStatus,
        // Review, as the student may see it: a send-back shows at once; an
        // accepted grade and its comment only once grades are released.
        int attemptsAllowed,
        GradingType gradingType,
        Integer maxMarks,
        BigDecimal score,
        Boolean passed,
        String feedback,
        LocalDateTime reviewedAt,
        LocalDateTime redoDueAt,
        boolean gradesReleased
) {}
