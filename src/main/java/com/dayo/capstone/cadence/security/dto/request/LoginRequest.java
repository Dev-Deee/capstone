package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Email or phone number is required")
    private String identifier;  // accepts either email or phone number

    @NotBlank(message = "Password is required")
    private String password;
}