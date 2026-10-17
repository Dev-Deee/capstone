package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.ValidEmail;

@Getter
@Setter
public class AdminChangeEmailRequest {
    @NotBlank(message = "New email is required")
    @ValidEmail
    private String newEmail;

    @NotBlank(message = "A reason is required for the audit trail")
    private String reason;
}