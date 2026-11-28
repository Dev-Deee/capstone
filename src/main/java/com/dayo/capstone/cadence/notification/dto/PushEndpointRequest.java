package com.dayo.capstone.cadence.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** Identifies one of the caller's subscriptions (in the body, so the endpoint stays out of access logs). */
public record PushEndpointRequest(@NotBlank(message = "endpoint is required") String endpoint) {}
