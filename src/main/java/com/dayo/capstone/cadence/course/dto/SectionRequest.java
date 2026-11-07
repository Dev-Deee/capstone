package com.dayo.capstone.cadence.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SectionRequest {

    @NotBlank(message = "Give the section a title")
    @Size(max = 200, message = "The title can be at most 200 characters")
    private String title;
}
