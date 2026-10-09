package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLATFORM_ADMIN oversees institutions (list, status) but never reaches inside one:
 * no switching in, no stand-in roles, no institution data. It is never handed out
 * by sign-up or invites.
 */
class PlatformAdminTest extends IntegrationTestBase {

    @Autowired JwtService jwtService;

    @Test
    void platformAdminCannotEnterAnInstitutionTheyDontBelongTo() {
        Institution other = institution("Other Inst");
        Cohort otherCohort = cohort(other);
        User platform = user("platform");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);

        Resp in = call("POST", "/api/auth/login", Map.of("identifier", platform.getEmail(), "password", PASSWORD), null);
        assertThat(in.status()).isEqualTo(200);
        assertThat(in.body().get("institutionPublicId").isNull()).isTrue();
        assertThat(in.body().get("roles").size()).isZero();

        String token = in.text("accessToken");
        assertThat(switchTo(token, other).status()).isEqualTo(404);
        assertThat(get("/api/cohorts/" + otherCohort.getPublicId(), token).status()).isIn(401, 403, 404);
        assertThat(get("/api/cohorts", token).status()).isIn(401, 403);
        assertThat(auditLogRepository.findAll()).noneMatch(l -> l.getAction() == AuditAction.PLATFORM_ACCESS);
    }

    @Test
    void aTokenNamingAnInstitutionWithoutMembershipIsRejected() {
        Institution other = institution("Forged Inst");
        User platform = user("platform-forged");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        String forged = jwtService.generateAccessToken(platform.getPublicId(), java.util.Set.of("SUPER_ADMIN"),
                other.getPublicId(), true);
        assertThat(get("/api/cohorts", forged).status()).isIn(401, 403);
        assertThat(get("/api/institutions/current", forged).status()).isIn(401, 403, 404);
    }

    @Test
    void aPlatformAdminWhoIsAlsoAMemberGetsOnlyThatMembershipsRoles() {
        Institution home = institution("Home Inst");
        User platform = user("platform-member");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        member(platform, home, Role.FACILITATOR);
        Resp in = switchTo(login(platform), home);
        assertThat(in.status()).isEqualTo(200);
        assertThat(in.body().get("roles").toString()).doesNotContain("SUPER_ADMIN").contains("FACILITATOR");
    }

    @Test
    void platformAdminSeesEveryInstitution_otherOwnersDoNot() {
        Institution x = institution("X Inst");
        Institution y = institution("Y Inst");
        User platform = user("platform2");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        member(platform, x, Role.SUPER_ADMIN);
        User ownerY = user("ownerY");
        member(ownerY, y, Role.SUPER_ADMIN);

        Resp all = get("/api/institutions", login(platform));
        assertThat(all.status()).isEqualTo(200);
        assertThat(all.body().findValuesAsText("publicId")).contains(x.getPublicId().toString(), y.getPublicId().toString());
        assertThat(get("/api/institutions", login(ownerY)).status()).isEqualTo(403);
    }

    @Test
    void platformAdminIsNeverGrantedBySignUp() {
        Resp r = call("POST", "/api/institutions/signup", Map.of(
                "institutionName", "Self Serve U", "slug", unique("self-serve"),
                "firstName", "Founder", "lastName", "One", "email", unique("founder") + "@test.cadence",
                "phoneNumber", "+234" + String.format("%010d", System.nanoTime() % 10_000_000_000L), "password", PASSWORD, "consentGiven", true), null);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("platformAdmin").asBoolean()).isFalse();
    }
}
