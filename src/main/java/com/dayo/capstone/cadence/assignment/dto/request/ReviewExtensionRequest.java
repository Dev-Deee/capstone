package com.dayo.capstone.cadence.assignment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewExtensionRequest {

    @NotNull(message = "Approval decision is required")
    private Boolean approve;

    private String reviewNote;
}
