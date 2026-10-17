package com.dayo.capstone.cadence.security;

import java.util.Set;
import java.util.UUID;

/**
 * The JWT subject is the user's publicId (IDOR rule, Section 18.1) —
 * the internal Long id never appears in a token, a claim, or anywhere
 * a client can see it.
 *
 * institutionPublicId is the tenant this request acts in (null only for a
 * platform admin outside any institution); roles are the caller's roles there.
 */
public record CadencePrincipal(
        UUID userPublicId,
        Set<String> roles,
        UUID institutionPublicId,
        boolean platformAdmin
) {}
