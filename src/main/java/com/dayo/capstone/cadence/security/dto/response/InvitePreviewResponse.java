package com.dayo.capstone.cadence.security.dto.response;

import lombok.Builder;

// What the accept-invite page shows before the invitee acts: where they're
// invited, as what, and whether to set a password (new) or sign in (existing).
@Builder
public record InvitePreviewResponse(
        String institutionName,
        String institutionSlug,
        String role,
        String email,
        String firstName,
        boolean accountExists
) {}
