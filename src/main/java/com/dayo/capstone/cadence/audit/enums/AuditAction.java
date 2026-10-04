package com.dayo.capstone.cadence.audit.enums;

public enum AuditAction {
    CREATE, UPDATE, DELETE, APPROVE, DENY, OVERRIDE, EXPORT,
    LOGIN, LOGIN_FAILED, LOGOUT, CONSENT_GIVEN, CONSENT_REVOKED, ACCOUNT_LOCKED,
    // Multi-tenant: a new institution founded; a platform admin entering an
    // institution they aren't a member of (the only cross-tenant access path).
    INSTITUTION_CREATED, PLATFORM_ACCESS
}