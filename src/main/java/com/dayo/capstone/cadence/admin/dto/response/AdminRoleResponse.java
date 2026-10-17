package com.dayo.capstone.cadence.admin.dto.response;

import com.dayo.capstone.cadence.common.enums.Permission;
import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record AdminRoleResponse(
        UUID publicId,
        String name,
        String description,
        Set<Permission> permissions,
        boolean isActive
) {}