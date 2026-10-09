package com.dayo.capstone.cadence.institution.enums;

// The single source of truth for whether an institution is usable
// (Institution.isUsable(): ACTIVE only). Self-serve sign-up is instant, so
// institutions start ACTIVE. PENDING (a future approval workflow) and
// SUSPENDED (disabled without deleting) both block members — sign-in,
// switching in, joining, and any request scoped to the institution — while a
// platform admin keeps full access. Nothing in the app sets them yet; they're
// set directly in the database until an admin action exists.
public enum InstitutionStatus {
    ACTIVE,
    PENDING,
    SUSPENDED
}
