package com.dayo.capstone.cadence.attendance.dto.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Returned by the public QR-route endpoint — deliberately excludes the
 * session code so that scanning the QR alone is never sufficient to mark
 * attendance. The student must still type the spoken code (Section 6.1).
 */
@Builder
public record SessionQrRouteResponse(
        UUID sessionPublicId,
        String cohortName,
        String subjectName,
        boolean isOpen
) {}