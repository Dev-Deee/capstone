package com.dayo.capstone.cadence.institution.dto.response;

// available=false comes with a human-readable reason and, where possible, a free suggestion.
public record SlugAvailabilityResponse(String slug, boolean available, String reason, String suggestion) {}
