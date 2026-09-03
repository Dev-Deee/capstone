package com.dayo.capstone.cadence.security.dto.request;

import com.dayo.capstone.cadence.user.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStaffInviteRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotNull(message = "Target role is required")
    private Role targetRole; // FACILITATOR or ADMIN only — enforced in service

    private String adminRolePublicId; // required if targetRole = ADMIN
    private String cohortPublicId;    // optional — scope for the AdminRole grant
}