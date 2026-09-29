package com.dayo.capstone.cadence.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateExtensionRequestRequest {

    @NotNull(message = "Requested new deadline is required")
    private LocalDateTime requestedNewDeadline;

    @NotBlank(message = "Reason is required")
    private String reason;
}
