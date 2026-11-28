package com.dayo.capstone.cadence.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The shape of the browser's {@code PushSubscription.toJSON()}, so the web app can send it as is. */
public record PushSubscriptionRequest(
        @NotBlank(message = "endpoint is required") @Size(max = 1000, message = "endpoint is too long") String endpoint,
        @NotNull(message = "keys are required") @Valid Keys keys
) {
    public record Keys(
            @NotBlank(message = "keys.p256dh is required") @Size(max = 100) String p256dh,
            @NotBlank(message = "keys.auth is required") @Size(max = 50) String auth
    ) {}
}
