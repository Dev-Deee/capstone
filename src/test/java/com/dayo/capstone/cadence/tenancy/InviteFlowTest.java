package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.security.model.StaffInviteToken;
import com.dayo.capstone.cadence.security.service.JwtService;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InviteFlowTest extends IntegrationTestBase {

    @Autowired JwtService jwtService;

    Institution inst;
    User owner;
    String ownerToken;

    @BeforeEach
    void setUp() {
        inst = institution("Invite Inst");
        owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        ownerToken = login(owner);
    }

    private Map<String, Object> invite(String email, Role role) {
        Map<String, Object> m = new HashMap<>();
        m.put("email", email);
        m.put("targetRole", role.name());
        m.put("firstName", "New");
        m.put("lastName", "Person");
        m.put("phoneNumber", "+234" + (System.nanoTime() % 10_000_000_000L));
        return m;
    }

    /** Creates an invite directly so the test knows the raw token (normally only emailed). */
    private String rawInvite(String email, Role role) {
        String raw = UUID.randomUUID().toString();
        StaffInviteToken t = new StaffInviteToken();
        t.setInstitution(inst);
        t.setEmail(email.toLowerCase());
        t.setRole(role);
        t.setFirstName("New");
        t.setLastName("Person");
        t.setPhoneNumber("+234" + (System.nanoTime() % 10_000_000_000L));
        t.setInvitedBy(owner);
        t.setTokenHash(jwtService.hashToken(raw));
        t.setExpiresAt(LocalDateTime.now().plusDays(7));
        inviteRepository.save(t);
        return raw;
    }

    @Test
    void invitingSomeoneWhoIsAlreadyAMemberIsRejected() {
        User existingMember = user("member");
        member(existingMember, inst, Role.FACILITATOR);
        Resp r = call("POST", "/api/auth/staff-invites", invite(existingMember.getEmail(), Role.FACILITATOR), ownerToken);
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.message()).contains("already a member");
    }

    @Test
    void inviteCreationDoesNotCreateAUser() {
        String email = unique("fresh") + "@test.cadence";
        assertThat(call("POST", "/api/auth/staff-invites", invite(email, Role.FACILITATOR), ownerToken).status()).isEqualTo(201);
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void brandNewInviteeSetsPasswordAndJoins() {
        String email = unique("brandnew") + "@test.cadence";
        String raw = rawInvite(email, Role.FACILITATOR);
        assertThat(get("/api/invites/" + raw, null).body().get("accountExists").asBoolean()).isFalse();

        Resp r = call("POST", "/api/auth/accept-invite", Map.of("token", raw, "password", PASSWORD), null);
        assertThat(r.status()).isEqualTo(200);
        User created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(membershipRepository.findByUserAndInstitution(created, inst).orElseThrow().getRoles()).containsExactly(Role.FACILITATOR);
        assertThat(created.isPlatformAdmin()).isFalse();
    }

    @Test
    void existingAccountInvitedToASecondInstitution_noSecondUser_justAMembership() {
        Institution home = institution("Home");
        User person = user("person");
        member(person, home, Role.STUDENT);

        String raw = rawInvite(person.getEmail(), Role.FACILITATOR);
        assertThat(get("/api/invites/" + raw, null).body().get("accountExists").asBoolean()).isTrue();
        // The set-a-password path is refused for an existing account…
        assertThat(call("POST", "/api/auth/accept-invite", Map.of("token", raw, "password", "Another-Pass-1!"), null).status()).isEqualTo(409);
        // …they accept signed in instead.
        Resp r = call("POST", "/api/invites/accept", Map.of("token", raw), login(person));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.text("institutionPublicId")).isEqualTo(inst.getPublicId().toString());

        assertThat(userRepository.findAll().stream().filter(u -> person.getEmail().equalsIgnoreCase(u.getEmail()))).hasSize(1);
        assertThat(membershipRepository.findByUserAndInstitution(person, home).orElseThrow().getRoles()).containsExactly(Role.STUDENT);
        assertThat(membershipRepository.findByUserAndInstitution(person, inst).orElseThrow().getRoles()).containsExactly(Role.FACILITATOR);
    }

    @Test
    void forwardedInviteCannotBeAcceptedByADifferentAccount() {
        Institution home = institution("Elsewhere");
        User intended = user("intended");
        member(intended, home, Role.STUDENT);
        User other = user("other");
        member(other, home, Role.STUDENT);

        String raw = rawInvite(intended.getEmail(), Role.FACILITATOR);
        Resp r = call("POST", "/api/invites/accept", Map.of("token", raw), login(other));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.message()).contains("different email");
        assertThat(membershipRepository.existsByUserAndInstitution(other, inst)).isFalse();
    }

    @Test
    void pendingInviteThenSelfSignUpThenAccept_works() {
        String email = unique("both") + "@test.cadence";
        String raw = rawInvite(email, Role.FACILITATOR);

        // The invitee founds their own institution before accepting.
        Map<String, Object> signup = new HashMap<>();
        signup.put("institutionName", "Own Place");
        signup.put("slug", unique("own-place"));
        signup.put("firstName", "Both");
        signup.put("lastName", "Ways");
        signup.put("email", email);
        signup.put("phoneNumber", "+234" + (System.nanoTime() % 10_000_000_000L));
        signup.put("password", PASSWORD);
        signup.put("consentGiven", true);
        Resp founded = call("POST", "/api/institutions/signup", signup, null);
        assertThat(founded.status()).isEqualTo(201);

        // The earlier invite still works, now as an existing account.
        assertThat(get("/api/invites/" + raw, null).body().get("accountExists").asBoolean()).isTrue();
        Resp r = call("POST", "/api/invites/accept", Map.of("token", raw), founded.text("accessToken"));
        assertThat(r.status()).isEqualTo(200);
        User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(u.getMemberships()).hasSize(2);
    }

    @Test
    void onlyASuperAdminCanInviteASuperAdmin() {
        User admin = user("admin");
        member(admin, inst, Role.ADMIN);
        String email = unique("co-owner") + "@test.cadence";
        assertThat(call("POST", "/api/auth/staff-invites", invite(email, Role.SUPER_ADMIN), login(admin)).status()).isEqualTo(403);
        assertThat(call("POST", "/api/auth/staff-invites", invite(email, Role.SUPER_ADMIN), ownerToken).status()).isEqualTo(201);
    }
}
