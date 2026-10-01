package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendEmailCodeRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
