package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyPhoneOtpRequest {
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "\\d{6}", message = "OTP code must be exactly 6 digits")
    private String code;
}
