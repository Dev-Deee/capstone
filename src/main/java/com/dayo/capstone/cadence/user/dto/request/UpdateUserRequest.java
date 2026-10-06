package com.dayo.capstone.cadence.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import com.dayo.capstone.cadence.common.validation.ValidPhone;

// Admin edit of another user's profile details. Email has its own audited
// endpoint (PATCH /api/auth/{id}/email) because changing it signs the user out.
@Getter
@Setter
public class UpdateUserRequest {
    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String middleName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @ValidPhone
    private String phoneNumber;

    @Size(max = 50)
    private String institutionStudentId;
}
