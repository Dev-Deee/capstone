package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionSignupTest extends IntegrationTestBase {

    private Map<String, Object> signup(String handle, String email) {
        Map<String, Object> m = new HashMap<>();
        m.put("institutionName", "Lagos Tech Academy");
        m.put("handle", handle);
        m.put("firstName", "Ada");
        m.put("lastName", "Founder");
        m.put("email", email);
        m.put("phoneNumber", "+234" + String.format("%010d", System.nanoTime() % 10_000_000_000L));
        m.put("password", PASSWORD);
        m.put("consentGiven", true);
        return m;
    }

    @Test
    void newUserFoundsInstitution_becomesItsSuperAdmin_audited() {
        String handle = unique("lagos-tech");
        String email = unique("ada") + "@test.cadence";
        Resp r = call("POST", "/api/institutions/signup", signup(handle, email), null);

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.text("institutionHandle")).isEqualTo(handle);
        assertThat(r.body().get("roles").toString()).contains("SUPER_ADMIN");
        Institution inst = institutionRepository.findByHandle(handle).orElseThrow();
        assertThat(inst.getStatus().name()).isEqualTo("ACTIVE");
        User founder = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(membershipRepository.findByUserAndInstitution(founder, inst).orElseThrow().getRoles())
                .containsExactly(Role.SUPER_ADMIN);
        assertThat(auditLogRepository.findAll()).anyMatch(l -> l.getAction() == AuditAction.INSTITUTION_CREATED
                && inst.getPublicId().toString().equals(l.getEntityPublicId()));
        // The returned token works straight away.
        assertThat(get("/api/institutions/current", r.text("accessToken")).text("handle")).isEqualTo(handle);
    }

    @Test
    void takenHandleIsRejectedWithASuggestion_andNothingIsCreated() {
        String handle = unique("dup");
        assertThat(call("POST", "/api/institutions/signup", signup(handle, unique("first") + "@test.cadence"), null).status()).isEqualTo(201);

        String secondEmail = unique("second") + "@test.cadence";
        Resp r = call("POST", "/api/institutions/signup", signup(handle, secondEmail), null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.message()).contains("already taken").contains(handle + "-2");
        assertThat(userRepository.findByEmailIgnoreCase(secondEmail)).isEmpty(); // rolled back, no orphan user

        Resp check = get("/api/institutions/handle-availability?handle=" + handle, null);
        assertThat(check.body().get("available").asBoolean()).isFalse();
        assertThat(check.text("suggestion")).isEqualTo(handle + "-2");
    }

    @Test
    void badOrReservedHandlesAreRejected() {
        assertThat(call("POST", "/api/institutions/signup", signup("Has Spaces!", unique("x") + "@test.cadence"), null).status()).isEqualTo(400);
        assertThat(call("POST", "/api/institutions/signup", signup("admin", unique("y") + "@test.cadence"), null).status()).isEqualTo(400);
        assertThat(get("/api/institutions/handle-availability?handle=Lagos%20Tech", null).text("suggestion")).isEqualTo("lagos-tech");
    }

    @Test
    void signUpWithAnExistingEmailIsRefused() {
        User existing = user("existing");
        Resp r = call("POST", "/api/institutions/signup", signup(unique("again"), existing.getEmail()), null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.message()).contains("Sign in");
    }

    @Test
    void existingStudentFoundsTheirOwnInstitution_firstMembershipUntouched() {
        Institution school = institution("Their School");
        User student = user("student");
        member(student, school, Role.STUDENT);

        String handle = unique("their-own");
        Resp r = call("POST", "/api/institutions", Map.of("name", "Their Own Academy", "handle", handle), login(student));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("roles").toString()).contains("SUPER_ADMIN");

        Institution own = institutionRepository.findByHandle(handle).orElseThrow();
        assertThat(membershipRepository.findByUserAndInstitution(student, school).orElseThrow().getRoles()).containsExactly(Role.STUDENT);
        assertThat(membershipRepository.findByUserAndInstitution(student, own).orElseThrow().getRoles()).containsExactly(Role.SUPER_ADMIN);
        assertThat(userRepository.findAll().stream().filter(u -> student.getEmail().equals(u.getEmail()))).hasSize(1);
    }

    @Test
    void handleIsImmutableEvenWhenTheInstitutionIsRenamed() {
        Institution inst = institution("Original Name");
        String original = inst.getHandle();
        inst.setName("Renamed");
        inst.setHandle("something-else");
        institutionRepository.saveAndFlush(inst);
        Institution reloaded = institutionRepository.findById(inst.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(institutionRepository.findByHandle(original)).isPresent();
        assertThat(institutionRepository.findByHandle("something-else")).isEmpty();
    }
}
