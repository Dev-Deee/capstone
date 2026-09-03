package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInviteCodeRequest {
    @NotBlank(message = "Cohort public ID is required")
    private String cohortPublicId;

    private Integer maxUses; // 0 or null = unlimited

    private Integer expiresInDays; // null = never expires
}