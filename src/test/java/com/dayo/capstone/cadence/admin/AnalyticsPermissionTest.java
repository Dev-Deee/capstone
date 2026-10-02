package com.dayo.capstone.cadence.admin;

import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VIEW_INSTITUTION_ANALYTICS gates the admin attendance reports: no grant is a
 * 403, an institution-wide grant sees every cohort, a cohort-scoped grant sees
 * only its cohort, and a grant never carries over to another institution.
 */
class AnalyticsPermissionTest extends IntegrationTestBase {

    Institution a, b;
    Cohort cohort1, cohort2;
    ResumptionSession resumption2;
    User admin;

    @BeforeEach
    void setUp() {
        a = institution("Analytics Academy");
        b = institution("Other College");
        cohort1 = cohort(a);
        cohort2 = cohort(a);
        resumption2 = resumptionSession(cohort2);
        admin = user("analyst");
        member(admin, a, Role.ADMIN);
    }

    private String overview() { return "/api/admin/attendance/cohorts"; }
    private String summary(Cohort c) { return "/api/admin/attendance/cohorts/" + c.getPublicId() + "/summary"; }
    private String roster(ResumptionSession r) { return "/api/resumption-sessions/" + r.getPublicId() + "/attendance"; }
    private String history(Cohort c) { return "/api/resumption-sessions?cohortPublicId=" + c.getPublicId(); }

    @Test
    void adminWithoutTheGrantIsForbidden() {
        grant(admin, a, Permission.MANAGE_COHORTS);
        String token = login(admin);
        assertThat(get(overview(), token).status()).isEqualTo(403);
        assertThat(get(summary(cohort1), token).status()).isEqualTo(403);
        assertThat(get(roster(resumption2), token).status()).isEqualTo(403);
        assertThat(get(history(cohort2), token).status()).isEqualTo(403);
    }

    @Test
    void institutionWideGrantSeesEveryCohort() {
        grant(admin, a, Permission.VIEW_INSTITUTION_ANALYTICS);
        String token = login(admin);
        Resp list = get(overview(), token);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().findValuesAsText("cohortPublicId"))
                .contains(cohort1.getPublicId().toString(), cohort2.getPublicId().toString());
        assertThat(get(summary(cohort1), token).status()).isEqualTo(200);
        assertThat(get(roster(resumption2), token).status()).isEqualTo(200);
    }

    @Test
    void cohortScopedGrantSeesOnlyItsCohort() {
        grant(admin, a, cohort1, Permission.VIEW_INSTITUTION_ANALYTICS);
        String token = login(admin);
        Resp list = get(overview(), token);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().findValuesAsText("cohortPublicId"))
                .containsExactly(cohort1.getPublicId().toString());
        assertThat(get(summary(cohort1), token).status()).isEqualTo(200);
        assertThat(get(summary(cohort2), token).status()).isEqualTo(403);
        assertThat(get(roster(resumption2), token).status()).isEqualTo(403);
        assertThat(get(history(cohort2), token).status()).isEqualTo(403);
    }

    @Test
    void grantInOneInstitutionDoesNotApplyInAnother() {
        grant(admin, a, Permission.VIEW_INSTITUTION_ANALYTICS);
        member(admin, b, Role.ADMIN);
        Cohort cohortB = cohort(b);
        String tokenB = tokenIn(admin, b);
        assertThat(get(overview(), tokenB).status()).isEqualTo(403);
        assertThat(get(summary(cohortB), tokenB).status()).isEqualTo(403);
    }

    @Test
    void resumptionRunnersCanReadTheirRosterButNotTheOverview() {
        grant(admin, a, cohort2, Permission.MANAGE_RESUMPTION_CODES);
        String token = login(admin);
        assertThat(get(roster(resumption2), token).status()).isEqualTo(200);
        assertThat(get(history(cohort2), token).status()).isEqualTo(200);
        assertThat(get(overview(), token).status()).isEqualTo(403);
    }

    @Test
    void superAdminNeedsNoGrant() {
        User owner = user("owner");
        member(owner, a, Role.SUPER_ADMIN);
        String token = login(owner);
        assertThat(get(overview(), token).body().findValuesAsText("cohortPublicId"))
                .contains(cohort1.getPublicId().toString(), cohort2.getPublicId().toString());
        assertThat(get(roster(resumption2), token).status()).isEqualTo(200);
    }
}
