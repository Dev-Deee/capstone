package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The platform console: platform admins only; status changes need a reason, are audited, and take effect. */
class PlatformConsoleTest extends IntegrationTestBase {

    User platform, owner, student;
    Institution inst;
    String platformToken;

    @BeforeEach
    void setUp() {
        inst = institution("Console Academy");
        owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        student = user("student");
        member(student, inst, Role.STUDENT);
        platform = user("platform");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        member(platform, institution("Platform Home"), Role.SUPER_ADMIN);
        platformToken = login(platform);
    }

    private Resp changeStatus(String status, String reason, String token) {
        return call("PATCH", "/api/platform/institutions/" + inst.getPublicId() + "/status",
                reason == null ? Map.of("status", status) : Map.of("status", status, "reason", reason), token);
    }

    private Resp loginResp(User u) {
        return call("POST", "/api/auth/login", Map.of("identifier", u.getEmail(), "password", PASSWORD), null);
    }

    @Test
    void onlyPlatformAdminsCanUseIt() {
        String ownerToken = login(owner);
        assertThat(get("/api/platform/overview", ownerToken).status()).isEqualTo(403);
        assertThat(get("/api/platform/institutions", ownerToken).status()).isEqualTo(403);
        assertThat(changeStatus("SUSPENDED", "no", ownerToken).status()).isEqualTo(403);
    }

    @Test
    void listsEveryInstitutionWithItsOwnerAndMembers() {
        Resp overview = get("/api/platform/overview", platformToken);
        assertThat(overview.status()).isEqualTo(200);
        assertThat(overview.body().get("institutions").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(overview.body().get("active").asLong()).isGreaterThanOrEqualTo(2);

        Resp list = get("/api/platform/institutions", platformToken);
        JsonNode row = null;
        for (JsonNode r : list.body()) if (r.get("publicId").asText().equals(inst.getPublicId().toString())) row = r;
        assertThat(row).isNotNull();
        assertThat(row.get("members").asInt()).isEqualTo(2);
        assertThat(row.get("ownerEmail").asText()).isEqualTo(owner.getEmail());
        assertThat(row.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void suspendingLocksMembersOutAndIsAudited_reactivatingLetsThemBackIn() {
        Resp suspended = changeStatus("SUSPENDED", "Unpaid invoice — \"final\" notice", platformToken);
        assertThat(suspended.status()).isEqualTo(200);
        assertThat(suspended.text("status")).isEqualTo("SUSPENDED");
        assertThat(loginResp(student).status()).isEqualTo(401);
        assertThat(auditLogRepository.findAll()).anyMatch(l -> l.getAction() == AuditAction.UPDATE
                && inst.getPublicId().toString().equals(String.valueOf(l.getEntityPublicId()))
                && l.getAfterState() != null && l.getAfterState().contains("SUSPENDED")
                && l.getAfterState().contains("\\\"final\\\" notice"));

        assertThat(changeStatus("ACTIVE", "Paid", platformToken).status()).isEqualTo(200);
        assertThat(loginResp(student).status()).isEqualTo(200);
    }

    @Test
    void aReasonIsRequiredAndANoOpChangeIsRefused() {
        Resp noReason = changeStatus("SUSPENDED", null, platformToken);
        assertThat(noReason.status()).isEqualTo(400);
        assertThat(noReason.message()).contains("reason");
        Resp same = changeStatus("ACTIVE", "already", platformToken);
        assertThat(same.status()).isEqualTo(400);
        assertThat(same.message()).contains("already active");
    }
}
