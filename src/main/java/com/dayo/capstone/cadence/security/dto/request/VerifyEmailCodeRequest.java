package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.ValidEmail;

@Getter
@Setter
public class VerifyEmailCodeRequest {
    @NotBlank(message = "Email is required")
    @ValidEmail
    private String email;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Verification code must be exactly 6 digits")
    private String code;
}
