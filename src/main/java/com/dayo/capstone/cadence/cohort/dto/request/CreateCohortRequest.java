package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateCohortRequest {
    @NotBlank(message = "Cohort name is required")
    private String name;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;
}