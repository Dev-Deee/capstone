package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PLATFORM_ADMIN is the only role with unscoped reach, and it is never handed out by sign-up or invites. */
class PlatformAdminTest extends IntegrationTestBase {

    @Test
    void platformAdminCanEnterAnyInstitutionAndActAsItsOwner_audited() {
        Institution home = institution("Home Inst");
        Institution other = institution("Other Inst");
        Cohort otherCohort = cohort(other);
        User platform = user("platform");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        member(platform, home, Role.FACILITATOR);

        Resp entered = switchTo(login(platform), other);
        assertThat(entered.status()).isEqualTo(200);
        assertThat(entered.body().get("roles").toString()).contains("SUPER_ADMIN");
        assertThat(get("/api/cohorts/" + otherCohort.getPublicId(), entered.text("accessToken")).status()).isEqualTo(200);
        assertThat(auditLogRepository.findAll()).anyMatch(l -> l.getAction() == AuditAction.PLATFORM_ACCESS
                && other.getPublicId().toString().equals(l.getEntityPublicId()));
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
                "phoneNumber", "+234" + System.nanoTime() % 10_000_000_000L, "password", PASSWORD, "consentGiven", true), null);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("platformAdmin").asBoolean()).isFalse();
    }
}
