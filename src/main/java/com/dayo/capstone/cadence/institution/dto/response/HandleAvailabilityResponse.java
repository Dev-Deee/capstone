package com.dayo.capstone.cadence.institution.dto.response;

public record HandleAvailabilityResponse(String handle, boolean available, String reason, String suggestion) {}
