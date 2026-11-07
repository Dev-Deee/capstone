package com.dayo.capstone.cadence.assignment.dto.request;

import com.dayo.capstone.cadence.assignment.enums.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Accept (with the grade the assignment's grading type needs) or send back to redo.
@Getter
@Setter
public class ReviewSubmissionRequest {

    @NotNull(message = "Choose accept or send back")
    private ReviewDecision decision;

    private BigDecimal score;     // MARKS or PERCENTAGE

    private Boolean passed;       // PASS_FAIL

    @Size(max = 5000, message = "Feedback can be at most 5000 characters")
    private String feedback;

    private LocalDateTime redoDueAt; // REDO only; defaults to 3 days from now
}
