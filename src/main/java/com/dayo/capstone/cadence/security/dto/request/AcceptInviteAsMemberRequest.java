package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// An existing, signed-in user accepting an invite into another institution.
@Getter
@Setter
public class AcceptInviteAsMemberRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
