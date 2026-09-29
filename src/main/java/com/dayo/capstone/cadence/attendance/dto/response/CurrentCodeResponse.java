package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

@Builder
public record CurrentCodeResponse(
        String code,
        long secondsRemaining
) {}
