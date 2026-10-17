package com.dayo.capstone.cadence.common.tenant;

import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;

/**
 * One-line tenant check for code that loads a record by its publicId and then
 * applies a role short-circuit (e.g. "SUPER_ADMIN may review any extension").
 * A record from another institution is reported as not found — the same answer
 * as a record that doesn't exist, so nothing leaks about other tenants.
 */
public final class TenantGuard {

    private TenantGuard() {}

    public static void requireCurrent(Long recordInstitutionId, String what) {
        Long current = TenantContext.institutionId();
        if (current == null || !current.equals(recordInstitutionId)) {
            throw new ResourceNotFoundException(what + " not found");
        }
    }
}
