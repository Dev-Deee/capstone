package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.ValidEmail;

@Getter
@Setter
public class SendEmailCodeRequest {
    @NotBlank(message = "Email is required")
    @ValidEmail
    private String email;
}
