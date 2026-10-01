package com.dayo.capstone.cadence.cohort.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Entries are trimmed and validated in the service (at least one recipient overall).
@Getter
@Setter
public class SendInviteCodeRequest {
    @Size(max = 200, message = "At most 200 emails per send")
    private List<String> emails = new ArrayList<>();

    @Size(max = 200, message = "At most 200 phone numbers per send")
    private List<String> phoneNumbers = new ArrayList<>();
}
