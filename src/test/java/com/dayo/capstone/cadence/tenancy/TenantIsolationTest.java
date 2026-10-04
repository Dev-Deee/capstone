package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.assignment.model.ExtensionRequest;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A SUPER_ADMIN of institution A must get 403/404 — never data — when touching
 * institution B. These are the old "SUPER_ADMIN always passes" short-circuits,
 * each now scoped to the admin's own institution.
 */
class TenantIsolationTest extends IntegrationTestBase {

    Institution a, b;
    User ownerA, facilitatorB, studentB;
    Cohort cohortA, cohortB;
    Subject subjectB;
    String tokenA;

    @BeforeEach
    void setUp() {
        a = institution("Alpha Academy");
        b = institution("Beta College");
        ownerA = user("ownerA");
        member(ownerA, a, Role.SUPER_ADMIN);
        facilitatorB = user("facB");
        member(facilitatorB, b, Role.FACILITATOR);
        studentB = user("stuB");
        member(studentB, b, Role.STUDENT);
        cohortA = cohort(a);
        cohortB = cohort(b);
        subjectB = subject(b);
        tokenA = login(ownerA);
    }

    @Test
    void superAdminSeesOnlyTheirOwnInstitutionsCohorts() {
        Resp list = get("/api/cohorts", tokenA);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().findValuesAsText("publicId"))
                .contains(cohortA.getPublicId().toString())
                .doesNotContain(cohortB.getPublicId().toString());
        assertThat(get("/api/cohorts/" + cohortB.getPublicId(), tokenA).status()).isEqualTo(404);
    }

    @Test
    void superAdminCannotChangeAnotherInstitutionsCohort() {
        Resp r = call("PATCH", "/api/cohorts/" + cohortB.getPublicId(), Map.of("name", "hijacked"), tokenA);
        assertThat(r.status()).isEqualTo(404);
        assertThat(cohortRepository.findById(cohortB.getId()).orElseThrow().getName()).isNotEqualTo("hijacked");
    }

    @Test
    void superAdminCannotSeeOrManageAnotherInstitutionsUsers() {
        assertThat(get("/api/users", tokenA).body().findValuesAsText("publicId"))
                .doesNotContain(studentB.getPublicId().toString());
        assertThat(get("/api/users/" + studentB.getPublicId(), tokenA).status()).isEqualTo(404);
        assertThat(call("PATCH", "/api/users/" + studentB.getPublicId() + "/deactivate", null, tokenA).status()).isEqualTo(404);
        assertThat(call("DELETE", "/api/users/" + studentB.getPublicId(), null, tokenA).status()).isEqualTo(404);
        assertThat(membershipRepository.existsByUserAndInstitution(studentB, b)).isTrue();
    }

    @Test
    void superAdminCannotReadAnotherInstitutionsAttendanceOrInviteCodes() {
        assertThat(get("/api/admin/attendance/cohorts/" + cohortB.getPublicId() + "/summary", tokenA).status()).isEqualTo(404);
        assertThat(get("/api/cohorts/" + cohortB.getPublicId() + "/invite-codes", tokenA).status()).isEqualTo(404);
    }

    @Test
    void superAdminCannotReadAnotherInstitutionsSessionRoster() {
        Session sessionB = session(cohortB, subjectB, facilitatorB);
        assertThat(get("/api/sessions/" + sessionB.getPublicId() + "/attendance", tokenA).status()).isEqualTo(404);
    }

    @Test
    void superAdminCannotReviewAnotherInstitutionsExtensionRequest() {
        ExtensionRequest req = extensionRequest(cohortB, subjectB, facilitatorB, studentB);
        Resp r = call("PATCH", "/api/extension-requests/" + req.getPublicId() + "/review", Map.of("approve", true), tokenA);
        assertThat(r.status()).isEqualTo(404);
        assertThat(extensionRequestRepository.findById(req.getId()).orElseThrow().getStatus().name()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void superAdminCannotAssignAnotherInstitutionsFacilitator() {
        Subject subjectA = subject(a);
        Resp r = call("POST", "/api/facilitator-cohorts", Map.of(
                "facilitatorPublicId", facilitatorB.getPublicId().toString(),
                "cohortPublicId", cohortA.getPublicId().toString(),
                "subjectPublicId", subjectA.getPublicId().toString()), tokenA);
        assertThat(r.status()).isEqualTo(400); // not a facilitator *here*
    }

    @Test
    void superAdminCannotListAllInstitutionsOrReadAnother() {
        assertThat(get("/api/institutions", tokenA).status()).isEqualTo(403); // platform admins only
        assertThat(get("/api/institutions/" + b.getPublicId(), tokenA).status()).isEqualTo(404);
        assertThat(get("/api/institutions/" + a.getPublicId(), tokenA).status()).isEqualTo(200);
    }

    @Test
    void superAdminCannotSwitchIntoAnInstitutionTheyDontBelongTo() {
        assertThat(switchTo(tokenA, b).status()).isEqualTo(404);
    }

    @Test
    void adminPermissionsDoNotCrossInstitutions() {
        // Holds MANAGE_COHORTS institution-wide in A; is an ADMIN in B with no grants there.
        User admin = user("crossAdmin");
        member(admin, a, Role.ADMIN);
        member(admin, b, Role.ADMIN);
        grant(admin, a, Permission.MANAGE_COHORTS);

        String inA = tokenIn(admin, a);
        assertThat(call("PATCH", "/api/cohorts/" + cohortA.getPublicId(), Map.of("name", unique("renamed")), inA).status())
                .isEqualTo(200);

        String inB = tokenIn(admin, b);
        assertThat(call("PATCH", "/api/cohorts/" + cohortB.getPublicId(), Map.of("name", unique("renamed")), inB).status())
                .isEqualTo(403);
    }

    @Test
    void rolesComeFromTheMembershipOfTheInstitutionInUse() {
        // Student in A, owner of B: owner powers only while working in B.
        User both = user("both");
        member(both, a, Role.STUDENT);
        member(both, b, Role.SUPER_ADMIN);

        Resp inA = switchTo(login(both), a);
        assertThat(inA.body().get("roles").toString()).contains("STUDENT").doesNotContain("SUPER_ADMIN");
        assertThat(get("/api/users", inA.text("accessToken")).status()).isEqualTo(403);

        Resp inB = switchTo(inA.text("accessToken"), b);
        assertThat(inB.body().get("roles").toString()).contains("SUPER_ADMIN").doesNotContain("STUDENT");
        assertThat(get("/api/users", inB.text("accessToken")).status()).isEqualTo(200);
    }

    @Test
    void removedMemberLosesAccessOnTheNextRequest() {
        User temp = user("temp");
        member(temp, a, Role.FACILITATOR);
        String token = login(temp);
        assertThat(get("/api/institutions/current", token).status()).isEqualTo(200);
        membershipRepository.delete(membershipRepository.findByUserAndInstitution(temp, a).orElseThrow());
        assertThat(get("/api/institutions/current", token).status()).isIn(401, 403);
    }
}
