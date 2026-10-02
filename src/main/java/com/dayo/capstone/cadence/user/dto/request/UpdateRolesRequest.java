package com.dayo.capstone.cadence.user.dto.request;

import com.dayo.capstone.cadence.user.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateRolesRequest {
    @NotEmpty(message = "Choose at least one role")
    private Set<Role> roles;
}
