package com.dayo.capstone.cadence.security.dto.request;

import com.dayo.capstone.cadence.user.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.ValidPhone;
import com.dayo.capstone.cadence.common.validation.ValidEmail;

@Getter
@Setter
public class CreateStaffInviteRequest {
    private String firstName;

    private String middleName;
    private String lastName;

    @NotBlank(message = "Email is required")
    @ValidEmail
    private String email;
    @ValidPhone
    private String phoneNumber;

    @NotNull(message = "Target role is required")
    private Role targetRole; // STUDENT, FACILITATOR, ADMIN or SUPER_ADMIN — rules in InviteService

    private String adminRolePublicId; // required if targetRole = ADMIN
    private String cohortPublicId;    // optional — ADMIN: scope of the AdminRole grant; STUDENT: cohort to enroll in
}