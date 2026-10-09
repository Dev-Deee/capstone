package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.StrongPassword;

@Getter
@Setter
public class AcceptStaffInviteRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @StrongPassword
    private String password;
}