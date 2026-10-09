package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortInviteCode;
import com.dayo.capstone.cadence.cohort.repository.CohortInviteCodeRepository;
import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.security.model.StaffInviteToken;
import com.dayo.capstone.cadence.security.service.JwtService;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Institution.status is the only switch: ACTIVE institutions work; PENDING and
 * SUSPENDED ones are closed to their members — sign-in, switching in, joining,
 * existing sessions and refresh — platform admins included.
 */
class InstitutionStatusTest extends IntegrationTestBase {

    @Autowired CohortInviteCodeRepository inviteCodeRepository;
    @Autowired JwtService jwtService;

    private Institution withStatus(String name, InstitutionStatus status) {
        Institution i = institution(name);
        i.setStatus(status);
        return institutionRepository.save(i);
    }

    private void setStatus(Institution i, InstitutionStatus status) {
        Institution fresh = institutionRepository.findById(i.getId()).orElseThrow();
        fresh.setStatus(status);
        institutionRepository.save(fresh);
    }

    private Resp loginResp(User u) {
        return call("POST", "/api/auth/login", Map.of("identifier", u.getEmail(), "password", PASSWORD), null);
    }

    @Test
    void anActiveInstitutionWorks() {
        Institution active = withStatus("Active Academy", InstitutionStatus.ACTIVE);
        cohort(active);
        User owner = user("owner");
        member(owner, active, Role.SUPER_ADMIN);
        String token = login(owner);
        assertThat(get("/api/cohorts", token).status()).isEqualTo(200);
        assertThat(get("/api/cohorts", token).body().size()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = InstitutionStatus.class, names = {"PENDING", "SUSPENDED"})
    void membersOfAnUnusableInstitutionCantSignIn(InstitutionStatus status) {
        Institution closed = withStatus("Closed " + status, status);
        User member = user("member");
        member(member, closed, Role.STUDENT);
        Resp r = loginResp(member);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.message()).contains("isn't available");
    }

    @Test
    void signInSkipsAnUnusableInstitutionForAUsableOne() {
        Institution closed = withStatus("Suspended First", InstitutionStatus.SUSPENDED);
        Institution open = withStatus("Still Open", InstitutionStatus.ACTIVE);
        User u = user("both");
        member(u, closed, Role.FACILITATOR);
        member(u, open, Role.FACILITATOR);
        u = userRepository.findById(u.getId()).orElseThrow();
        u.setLastActiveInstitution(closed);
        userRepository.save(u);

        Resp r = loginResp(u);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.text("institutionPublicId")).isEqualTo(open.getPublicId().toString());
        assertThat(switchTo(r.text("accessToken"), closed).status()).isEqualTo(403);
    }

    @Test
    void suspendingClosesOpenSessionsAndRefresh() {
        Institution inst = withStatus("Soon Suspended", InstitutionStatus.ACTIVE);
        Institution other = withStatus("Other Home", InstitutionStatus.ACTIVE);
        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        member(owner, other, Role.SUPER_ADMIN);
        Resp session = switchTo(login(owner), inst);
        String token = session.text("accessToken");
        assertThat(get("/api/cohorts", token).status()).isEqualTo(200);

        setStatus(inst, InstitutionStatus.SUSPENDED);

        // Still signed in, but nothing in the suspended institution is reachable…
        assertThat(get("/api/cohorts", token).status()).isEqualTo(403);
        assertThat(call("POST", "/api/cohorts", Map.of("name", "x", "startDate", "2026-10-01"), token).status()).isEqualTo(403);
        // …the session can't be renewed there…
        assertThat(call("POST", "/api/auth/refresh", Map.of("refreshToken", session.text("refreshToken")), null).status())
                .isEqualTo(401);
        // …and moving to a usable institution still works.
        Resp moved = switchTo(token, other);
        assertThat(moved.status()).isEqualTo(200);
        assertThat(get("/api/cohorts", moved.text("accessToken")).status()).isEqualTo(200);
    }

    @Test
    void aSuspendedInstitutionIsClosedToAPlatformAdminMemberToo_whoStillReachesTheConsole() {
        Institution suspended = withStatus("Suspended For Review", InstitutionStatus.SUSPENDED);
        User platform = user("platform");
        platform.setPlatformAdmin(true);
        userRepository.save(platform);
        member(platform, suspended, Role.SUPER_ADMIN);

        Resp in = loginResp(platform);
        assertThat(in.status()).isEqualTo(200);
        assertThat(in.body().get("institutionPublicId").isNull()).isTrue();
        assertThat(switchTo(in.text("accessToken"), suspended).status()).isEqualTo(403);
        assertThat(get("/api/platform/institutions", in.text("accessToken")).status()).isEqualTo(200);
    }

    @Test
    void nobodyCanJoinAnUnusableInstitutionByCode() {
        Institution suspended = withStatus("No New Joiners", InstitutionStatus.SUSPENDED);
        Cohort c = cohort(suspended);
        CohortInviteCode code = new CohortInviteCode();
        code.setCohort(c);
        code.setCode(unique("JOIN").toUpperCase());
        inviteCodeRepository.save(code);

        Resp joinAttempt = call("POST", "/api/auth/register", Map.of(
                "inviteCode", code.getCode(), "firstName", "New", "lastName", "Student",
                "email", unique("new").toLowerCase() + "@example.com",
                "phoneNumber", "+234" + String.format("%010d", System.nanoTime() % 10_000_000_000L),
                "password", PASSWORD, "consentGiven", true), null);
        assertThat(joinAttempt.status()).as(joinAttempt.toString()).isEqualTo(400);
        assertThat(joinAttempt.message()).contains("no longer active");
    }

    @Test
    void institutionResponsesCarryStatusAndNoActiveFlag() {
        Institution inst = withStatus("Json Shape U", InstitutionStatus.ACTIVE);
        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        Resp r = get("/api/institutions/current", login(owner));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.text("status")).isEqualTo("ACTIVE");
        assertThat(r.body().has("isActive")).isFalse();
        assertThat(r.body().has("active")).isFalse();
    }

    @Test
    void anInviteIntoASuspendedInstitutionCantBeAcceptedAndLeavesNothingBehind() {
        Institution inst = withStatus("Suspended Before Accept", InstitutionStatus.ACTIVE);
        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        String raw = UUID.randomUUID().toString();
        StaffInviteToken t = new StaffInviteToken();
        t.setInstitution(inst);
        String email = unique("invitee").toLowerCase() + "@example.com";
        t.setEmail(email);
        t.setRole(Role.FACILITATOR);
        t.setFirstName("New");
        t.setLastName("Person");
        t.setPhoneNumber("08012345678");
        t.setInvitedBy(owner);
        t.setTokenHash(jwtService.hashToken(raw));
        t.setExpiresAt(LocalDateTime.now().plusDays(7));
        inviteRepository.save(t);

        setStatus(inst, InstitutionStatus.SUSPENDED);

        Resp r = call("POST", "/api/auth/accept-invite", Map.of("token", raw, "password", PASSWORD), null);
        assertThat(r.status()).isEqualTo(403);
        // The whole acceptance rolled back: no membership, and the invite is still unused.
        assertThat(userRepository.findByEmailIgnoreCase(email)
                .map(u -> membershipRepository.existsByUserAndInstitution(u, inst)).orElse(false)).isFalse();
        assertThat(inviteRepository.findById(t.getId()).orElseThrow().isUsed()).isFalse();
    }
}
