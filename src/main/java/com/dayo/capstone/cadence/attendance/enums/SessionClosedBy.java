package com.dayo.capstone.cadence.attendance.enums;

import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;

// Who closed a session/resumption session. Mirrors the roles that can
// actually close one today (MANAGEMENT was removed along with that Role).
public enum SessionClosedBy {
    FACILITATOR,
    ADMIN,
    SUPER_ADMIN,
    SYSTEM_EXPIRY;

    // Highest role wins — a SUPER_ADMIN who also holds FACILITATOR is recorded as SUPER_ADMIN.
    public static SessionClosedBy forCaller(User caller) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return SUPER_ADMIN;
        if (caller.hasRole(Role.ADMIN)) return ADMIN;
        return FACILITATOR;
    }
}
