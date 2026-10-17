package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Returned by the public QR-route endpoint for resumption sessions.
 * Excludes the resumption code — the student must still type it themselves.
 */
@Builder
public record ResumptionSessionQrRouteResponse(
        UUID resumptionSessionPublicId,
        String cohortName,
        boolean isOpen
) {}