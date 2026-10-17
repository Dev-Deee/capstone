package com.dayo.capstone.cadence.institution.dto.response;

public record SlugAvailabilityResponse(String slug, boolean available, String reason, String suggestion) {}
