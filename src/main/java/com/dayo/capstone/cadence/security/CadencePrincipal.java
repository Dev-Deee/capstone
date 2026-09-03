package com.dayo.capstone.cadence.security;

import java.util.Set;
import java.util.UUID;

/**
 * The JWT subject is the user's publicId (IDOR rule, Section 18.1) —
 * the internal Long id never appears in a token, a claim, or anywhere
 * a client can see it.
 */
public record CadencePrincipal(
        UUID userPublicId,
        Set<String> roles
) {}