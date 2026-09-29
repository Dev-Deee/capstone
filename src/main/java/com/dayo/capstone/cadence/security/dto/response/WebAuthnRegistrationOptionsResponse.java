package com.dayo.capstone.cadence.security.dto.response;

import lombok.Builder;

@Builder
public record WebAuthnRegistrationOptionsResponse(
        String challenge,
        String userId,
        String userName,
        String rpId,
        String rpName
) {}