package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SwitchInstitutionRequest {
    @NotNull(message = "Institution is required")
    private UUID institutionPublicId;
}
