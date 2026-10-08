package com.dayo.capstone.cadence.institution.dto.request;

import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Platform console: move an institution between ACTIVE / PENDING / SUSPENDED. Always with a reason (audited).
@Getter
@Setter
public class ChangeInstitutionStatusRequest {
    @NotNull(message = "Status is required (ACTIVE, PENDING or SUSPENDED)")
    private InstitutionStatus status;

    @NotBlank(message = "A reason is required — it's kept in the audit log")
    @Size(max = 500, message = "Keep the reason under 500 characters")
    private String reason;
}
