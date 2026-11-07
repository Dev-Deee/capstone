package com.dayo.capstone.cadence.assignment.dto.response;

import com.dayo.capstone.cadence.assignment.enums.ReviewDecision;
import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// One student's work on an assignment, as the reviewing facilitator sees it.
@Builder
public record ReviewRowResponse(
        UUID publicId,
        UUID studentPublicId,
        String studentName,
        String studentEmail,
        SubmissionStatus status,
        SubmissionType submissionType,
        String linkUrl,
        String fileUrl,
        LocalDateTime submittedAt,
        boolean late,
        LocalDateTime effectiveDeadline,
        int resubmissionCount,
        int attemptsAllowed,
        BigDecimal score,
        Boolean passed,
        String feedback,
        LocalDateTime reviewedAt,
        String reviewedByName,
        LocalDateTime redoDueAt,
        List<Attempt> attempts
) {
    @Builder
    public record Attempt(
            int attemptNumber,
            LocalDateTime submittedAt,
            SubmissionType submissionType,
            String linkUrl,
            String fileUrl,
            ReviewDecision decision,
            BigDecimal score,
            Boolean passed,
            String feedback,
            String reviewedByName,
            LocalDateTime reviewedAt
    ) {}
}
