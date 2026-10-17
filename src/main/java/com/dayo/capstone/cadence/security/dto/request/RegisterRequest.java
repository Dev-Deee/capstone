package com.dayo.capstone.cadence.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.StrongPassword;
import com.dayo.capstone.cadence.common.validation.ValidPhone;
import com.dayo.capstone.cadence.common.validation.ValidEmail;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank(message = "Invite code is required")
    private String inviteCode;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @ValidEmail
    private String email;

    @NotBlank(message = "Phone number is required")
    @ValidPhone
    private String phoneNumber;

    // RegisterRequest.java — add
    private String institutionStudentId;  // optional, self-entered, no
    // validation beyond basic format
    // sanity (no uniqueness generation
    // logic — that's the institution's
    // scheme, not ours to enforce)

    @NotBlank(message = "Password is required")
    @StrongPassword
    private String password;

    @NotNull(message = "Consent must be explicitly given")
    private Boolean consentGiven;
}