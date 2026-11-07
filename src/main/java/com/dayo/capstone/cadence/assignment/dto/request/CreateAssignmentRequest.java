package com.dayo.capstone.cadence.assignment.dto.request;

import com.dayo.capstone.cadence.assignment.enums.GradingType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateAssignmentRequest {

    @NotBlank(message = "Cohort public ID is required")
    private String cohortPublicId;

    @NotBlank(message = "Subject public ID is required")
    private String subjectPublicId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Deadline is required")
    private LocalDateTime deadline;

    private Integer maxResubmissions;

    // Defaults to MARKS out of 100.
    private GradingType gradingType;

    @Min(value = 1, message = "Marks must be out of at least 1")
    @Max(value = 1000, message = "Marks can be out of at most 1000")
    private Integer maxMarks;
}
