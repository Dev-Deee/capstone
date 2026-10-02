package com.dayo.capstone.cadence.common.tenant;

import java.util.UUID;

/**
 * The institution the current request is acting in, set by JwtAuthFilter from
 * the access token and cleared when the request ends.
 *
 * This is the single place tenant scope comes from: User.hasRole()/getRoles()
 * answer for this institution only, and InstitutionService resolves "the
 * caller's institution" from it. A SUPER_ADMIN is therefore only ever a
 * super admin of the institution in context — never of another tenant.
 *
 * platformAdminActing is true only when a platform admin has entered an
 * institution they are not otherwise a member of; it is the one path with
 * cross-institution reach, and it is granted only at token issuance.
 */
public final class TenantContext {

    public record Scope(UUID userPublicId, Long institutionId, UUID institutionPublicId, boolean platformAdminActing) {}

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Scope scope) { CURRENT.set(scope); }

    public static void clear() { CURRENT.remove(); }

    public static Scope get() { return CURRENT.get(); }

    public static Long institutionId() {
        Scope s = CURRENT.get();
        return s == null ? null : s.institutionId();
    }

    public static UUID institutionPublicId() {
        Scope s = CURRENT.get();
        return s == null ? null : s.institutionPublicId();
    }

    /** True only for the given user, when they are a platform admin acting inside the current institution. */
    public static boolean isPlatformAdminActing(UUID userPublicId) {
        Scope s = CURRENT.get();
        return s != null && s.platformAdminActing() && s.userPublicId().equals(userPublicId);
    }
}
