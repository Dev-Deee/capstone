package com.dayo.capstone.cadence.user.dto.response;

import com.dayo.capstone.cadence.user.enums.Role;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Builder
public record UserResponse(
        UUID publicId,
        UUID institutionPublicId,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        String email,
        String phoneNumber,
        String profileImagesUrl,
        Set<Role> roles,
        boolean consentGiven,
        boolean webAuthnCapable,
        LocalDateTime createdAt
) {}