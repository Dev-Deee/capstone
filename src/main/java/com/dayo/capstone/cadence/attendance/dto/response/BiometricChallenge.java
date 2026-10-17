package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.util.List;

// What the browser needs for navigator.credentials.get(): a single-use
// challenge, the relying party, and the student's own registered credentials.
@Builder
public record BiometricChallenge(
        String challenge,
        String rpId,
        List<String> credentialIds
) {}
