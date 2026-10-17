package com.dayo.capstone.cadence.institution.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateSubjectRequest(
        @NotBlank String name
) {}