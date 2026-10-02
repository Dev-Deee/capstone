package com.dayo.capstone.cadence.institution.enums;

// Self-serve sign-up is instant, so every institution starts ACTIVE.
// PENDING (a future approval workflow) and SUSPENDED (disable without deleting)
// are reserved values only — nothing acts on them yet.
public enum InstitutionStatus {
    ACTIVE,
    PENDING,
    SUSPENDED
}
