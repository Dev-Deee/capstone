package com.dayo.capstone.cadence.institution.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// A signed-in user founds an additional institution.
@Getter
@Setter
public class FoundInstitutionRequest {
    @NotBlank(message = "Institution name is required")
    @Size(max = 120)
    private String name;

    @NotBlank(message = "Choose an address for your institution")
    private String slug;
}
