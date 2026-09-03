package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminChangeEmailRequest {
    @NotBlank(message = "New email is required")
    @Email(message = "Email must be valid")
    private String newEmail;

    @NotBlank(message = "A reason is required for the audit trail")
    private String reason;
}