package com.dayo.capstone.cadence.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateAdminRoleRequest {
    @NotBlank(message = "Role name is required")
    private String name;

    private String description;

    @NotEmpty(message = "At least one permission is required")
    private Set<String> permissions;
}