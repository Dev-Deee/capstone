package com.dayo.capstone.cadence.institution.enums;

// The single source of truth for whether an institution is usable
// (Institution.isUsable(): anything but SUSPENDED). Self-serve sign-up is
// instant, so institutions start ACTIVE. PENDING marks one awaiting review by
// the platform; it stays open to its members. SUSPENDED (disabled without
// deleting) blocks members — sign-in, switching in, joining, and any request
// scoped to the institution. Status is changed from the platform console.
public enum InstitutionStatus {
    ACTIVE,
    PENDING,
    SUSPENDED
}
