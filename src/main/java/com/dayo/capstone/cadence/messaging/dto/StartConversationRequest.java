package com.dayo.capstone.cadence.messaging.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class StartConversationRequest {

    @NotNull(message = "Choose who to message")
    private UUID userPublicId;
}
