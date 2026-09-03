package com.dayo.capstone.cadence.institution.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInstitutionRequest {

    @NotBlank(message = "Institution name is required")
    private String name;

    @NotBlank(message = "Institution address is required")
    private String address;
}