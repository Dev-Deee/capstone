package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** An institution must never be left without an active SUPER_ADMIN. */
class LastSuperAdminTest extends IntegrationTestBase {

    @Test
    void soleOwnerCannotDemoteThemselvesLeaveOrDeleteTheirAccount() {
        Institution inst = institution("Solo");
        User owner = user("solo");
        member(owner, inst, Role.SUPER_ADMIN);
        String token = login(owner);

        Resp demote = call("PUT", "/api/users/" + owner.getPublicId() + "/roles", Map.of("roles", List.of("ADMIN")), token);
        assertThat(demote.status()).isEqualTo(400);
        assertThat(demote.message()).contains("only super admin");

        assertThat(call("POST", "/api/users/me/leave-institution", null, token).status()).isEqualTo(400);
        assertThat(call("DELETE", "/api/users/me", null, token).status()).isEqualTo(400);
        assertThat(membershipRepository.findByUserAndInstitution(owner, inst).orElseThrow().getRoles()).contains(Role.SUPER_ADMIN);
    }

    @Test
    void withASecondOwnerTheFirstCanStepDown() {
        Institution inst = institution("Duo");
        User first = user("first");
        member(first, inst, Role.SUPER_ADMIN);
        User second = user("second");
        member(second, inst, Role.SUPER_ADMIN);

        Resp demote = call("PUT", "/api/users/" + first.getPublicId() + "/roles", Map.of("roles", List.of("ADMIN")), login(second));
        assertThat(demote.status()).isEqualTo(200);
        assertThat(membershipRepository.findByUserAndInstitution(first, inst).orElseThrow().getRoles()).containsExactly(Role.ADMIN);

        // Now `second` is the sole owner again and can't leave.
        assertThat(call("POST", "/api/users/me/leave-institution", null, login(second)).status()).isEqualTo(400);
    }

    @Test
    void ownerCanRemoveAnotherOwnerOnlyWhileOneRemains() {
        Institution inst = institution("Trio");
        User a = user("ownA");
        member(a, inst, Role.SUPER_ADMIN);
        User b = user("ownB");
        member(b, inst, Role.SUPER_ADMIN);

        assertThat(call("DELETE", "/api/users/" + b.getPublicId(), null, login(a)).status()).isEqualTo(204);
        assertThat(membershipRepository.existsByUserAndInstitution(b, inst)).isFalse();
        assertThat(userRepository.findById(b.getId())).isPresent(); // account itself untouched
    }
}
