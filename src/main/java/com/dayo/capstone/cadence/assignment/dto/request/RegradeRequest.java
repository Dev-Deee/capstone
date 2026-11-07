package com.dayo.capstone.cadence.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Changing an accepted grade. The reason goes into the audit log.
@Getter
@Setter
public class RegradeRequest {

    private BigDecimal score;

    private Boolean passed;

    @Size(max = 5000, message = "Feedback can be at most 5000 characters")
    private String feedback;

    @NotBlank(message = "Say why the grade is changing")
    @Size(max = 500, message = "The reason can be at most 500 characters")
    private String reason;
}
