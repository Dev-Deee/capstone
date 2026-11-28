package com.dayo.capstone.cadence.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AnnouncementRequest {

    @NotBlank(message = "Give the announcement a title")
    @Size(max = 200, message = "The title can be at most 200 characters")
    private String title;

    @NotBlank(message = "Write the announcement")
    @Size(max = 4000, message = "An announcement can be at most 4000 characters")
    private String body;

    @NotEmpty(message = "Choose at least one cohort")
    private List<UUID> cohortPublicIds = new ArrayList<>();
}
