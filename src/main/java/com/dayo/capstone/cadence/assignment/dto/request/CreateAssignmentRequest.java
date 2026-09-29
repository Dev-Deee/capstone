package com.dayo.capstone.cadence.assignment.dto.request;

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
}
