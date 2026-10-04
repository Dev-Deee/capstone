package com.dayo.capstone.cadence.institution.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Self-serve sign-up: a brand-new person founds a brand-new institution.
@Getter
@Setter
public class InstitutionSignupRequest {
    @NotBlank(message = "Institution name is required")
    @Size(max = 120)
    private String institutionName;

    @NotBlank(message = "Choose an address for your institution")
    private String slug;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull(message = "Consent must be explicitly given")
    private Boolean consentGiven;
}
