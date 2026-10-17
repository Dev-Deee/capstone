package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.StrongPassword;

@Getter
@Setter
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @StrongPassword
    private String newPassword;
}