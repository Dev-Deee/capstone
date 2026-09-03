package com.dayo.capstone.cadence.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignAdminRoleRequest {
    @NotBlank(message = "User public ID is required")
    private String userPublicId;

    @NotBlank(message = "Admin role public ID is required")
    private String adminRolePublicId;

    private String cohortPublicId; // nullable — omit for institution-wide
}