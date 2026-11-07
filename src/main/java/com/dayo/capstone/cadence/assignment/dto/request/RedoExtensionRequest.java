package com.dayo.capstone.cadence.assignment.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// More time and/or one more attempt for a student, at the facilitator's discretion.
@Getter
@Setter
public class RedoExtensionRequest {

    private LocalDateTime redoDueAt;

    private boolean grantExtraAttempt;
}
