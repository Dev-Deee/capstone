package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A platform admin belongs to no institution but can still change their own
 * password: the current one is checked, the new one must be strong, every
 * refresh token is revoked, and the change is audited (never the password).
 */
class PlatformAdminPasswordTest extends IntegrationTestBase {

    User platform;
    String token;
    String refreshToken;

    @BeforeEach
    void setUp() {
        platform = user("platform-pw");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        Resp in = signIn(PASSWORD);
        assertThat(in.status()).isEqualTo(200);
        assertThat(in.body().get("institutionPublicId").isNull()).isTrue();
        token = in.text("accessToken");
        refreshToken = in.text("refreshToken");
    }

    private Resp signIn(String password) {
        return call("POST", "/api/auth/login", Map.of("identifier", platform.getEmail(), "password", password), null);
    }

    private Resp change(String current, String next) {
        return call("POST", "/api/auth/change-password", Map.of("currentPassword", current, "newPassword", next), token);
    }

    // Generated per run, never a fixed credential.
    private static String strongPassword() {
        return "Pw-" + UUID.randomUUID().toString().substring(0, 12) + "-Xy9!";
    }

    @Test
    void changesWithTheRightCurrentPassword_oldOneStopsWorking_sessionsRevoked_audited() {
        String next = strongPassword();
        assertThat(change(PASSWORD, next).status()).isEqualTo(204);

        assertThat(signIn(PASSWORD).status()).isEqualTo(401);
        assertThat(signIn(next).status()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/refresh", Map.of("refreshToken", refreshToken), null).status())
                .isNotEqualTo(200);
        assertThat(auditLogRepository.findAll()).anyMatch(l -> l.getAction() == AuditAction.UPDATE
                && "UserPassword".equals(l.getEntityType())
                && platform.getPublicId().toString().equals(l.getEntityPublicId())
                && "PLATFORM_ADMIN".equals(l.getActorRole())
                && l.getAfterState() != null && !l.getAfterState().contains(next));
        // Still no way into any institution.
        assertThat(get("/api/cohorts", token).status()).isIn(401, 403);
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        Resp r = change("Not-" + strongPassword(), strongPassword());
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.message()).contains("Current password is incorrect");
        assertThat(signIn(PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void weakNewPasswordIsRejected() {
        Resp r = change(PASSWORD, "alllowercase");
        assertThat(r.status()).isEqualTo(400);
        assertThat(signIn(PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void signedOutCallersCannotChangeAPassword() {
        Resp r = call("POST", "/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", strongPassword()), null);
        assertThat(r.status()).isIn(401, 403);
    }
}
