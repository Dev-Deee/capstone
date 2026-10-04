package com.dayo.capstone.cadence.attendance;

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
 * Closing a campus resumption follows the same rule as opening one:
 * facilitators and super admins, or admins with MANAGE_RESUMPTION_CODES for
 * the cohort. (Facilitators could open one but not close it.)
 */
class ResumptionCloseTest extends IntegrationTestBase {

    Institution inst;
    Cohort cohort;
    ResumptionSession resumption;

    @BeforeEach
    void setUp() {
        inst = institution("Resumption Close College");
        cohort = cohort(inst);
        resumption = resumptionSession(cohort);
    }

    private Resp close(User who) {
        return call("PATCH", "/api/resumption-sessions/" + resumption.getPublicId() + "/close", null, login(who));
    }

    @Test
    void facilitatorCanCloseTheResumptionTheyOpened() {
        User fac = user("fac");
        member(fac, inst, Role.FACILITATOR);
        Resp r = close(fac);
        assertThat(r.status()).isEqualTo(200);
        ResumptionSession after = resumptionSessionRepository.findById(resumption.getId()).orElseThrow();
        assertThat(after.isOpen()).isFalse();
        assertThat(after.getClosedBy().name()).isEqualTo("FACILITATOR");
    }

    @Test
    void adminsNeedThePermissionAndOutsidersGetNotFound() {
        User admin = user("admin");
        member(admin, inst, Role.ADMIN);
        grant(admin, inst, Permission.MANAGE_COHORTS);
        assertThat(close(admin).status()).isEqualTo(403);

        User student = user("stu");
        member(student, inst, Role.STUDENT);
        assertThat(close(student).status()).isEqualTo(403);

        Institution other = institution("Elsewhere");
        User otherFac = user("otherfac");
        member(otherFac, other, Role.FACILITATOR);
        assertThat(close(otherFac).status()).isEqualTo(404);
        assertThat(resumptionSessionRepository.findById(resumption.getId()).orElseThrow().isOpen()).isTrue();
    }
}
