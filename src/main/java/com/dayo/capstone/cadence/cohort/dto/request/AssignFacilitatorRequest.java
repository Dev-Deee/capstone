package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignFacilitatorRequest {
    @NotBlank(message = "Facilitator public ID is required")
    private String facilitatorPublicId;

    @NotBlank(message = "Cohort public ID is required")
    private String cohortPublicId;

    @NotBlank(message = "Subject public ID is required")
    private String subjectPublicId;
}