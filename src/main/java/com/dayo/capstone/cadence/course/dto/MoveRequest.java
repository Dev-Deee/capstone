package com.dayo.capstone.cadence.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Move a section or material one place up or down.
@Getter
@Setter
public class MoveRequest {

    public enum Direction { UP, DOWN }

    @NotNull(message = "Say which way to move it")
    private Direction direction;
}
