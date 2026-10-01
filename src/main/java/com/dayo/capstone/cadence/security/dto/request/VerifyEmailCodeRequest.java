package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailCodeRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Verification code must be exactly 6 digits")
    private String code;
}
