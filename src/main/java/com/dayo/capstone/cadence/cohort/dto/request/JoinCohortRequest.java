package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinCohortRequest {
    @NotBlank(message = "Invite code is required")
    private String inviteCode;
}
