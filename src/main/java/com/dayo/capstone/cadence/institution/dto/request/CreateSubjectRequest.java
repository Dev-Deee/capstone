package com.dayo.capstone.cadence.institution.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubjectRequest {
    @NotBlank(message = "Subject name is required")
    private String name;

    private String description;
}