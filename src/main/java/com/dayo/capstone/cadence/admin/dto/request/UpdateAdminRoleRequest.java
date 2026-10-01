package com.dayo.capstone.cadence.admin.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateAdminRoleRequest {
    private String name;
    private String description;
    private Set<String> permissions; // null = leave unchanged; empty set = clear all
    private Boolean isActive;
}