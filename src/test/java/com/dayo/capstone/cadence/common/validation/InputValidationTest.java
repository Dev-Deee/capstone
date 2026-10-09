package com.dayo.capstone.cadence.common.validation;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The email / phone / password rules on real endpoints: a clear 400, and nothing created. */
class InputValidationTest extends IntegrationTestBase {

    private Map<String, Object> signup(String email, String phone, String password) {
        Map<String, Object> m = new HashMap<>();
        m.put("institutionName", "Validation Academy");
        m.put("slug", unique("valid"));
        m.put("firstName", "Ada");
        m.put("lastName", "Founder");
        m.put("email", email);
        m.put("phoneNumber", phone);
        m.put("password", password);
        m.put("consentGiven", true);
        return m;
    }

    private String freshEmail() {
        return unique("ada").toLowerCase() + "@example.com";
    }

    @Test
    void signupRejectsBadEmailPhoneAndWeakPasswords() {
        Resp badEmail = call("POST", "/api/institutions/signup", signup("ada@localhost", "08012345678", PASSWORD), null);
        assertThat(badEmail.status()).isEqualTo(400);
        assertThat(badEmail.message()).isEqualTo(ValidationPatterns.EMAIL_MESSAGE);

        Resp badPhone = call("POST", "/api/institutions/signup", signup(freshEmail(), "12345", PASSWORD), null);
        assertThat(badPhone.status()).isEqualTo(400);
        assertThat(badPhone.message()).isEqualTo(ValidationPatterns.PHONE_MESSAGE);

        Resp weak = call("POST", "/api/institutions/signup", signup(freshEmail(), "08012345678", "password1"), null);
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.message()).isEqualTo(ValidationPatterns.PASSWORD_MESSAGE);

        String email = freshEmail();
        assertThat(call("POST", "/api/institutions/signup", signup(email, "08012345678", "Str0ng!pass"), null).status())
                .isEqualTo(201);
        assertThat(userRepository.findByEmailIgnoreCase("ada@localhost")).isEmpty();
    }

    @Test
    void invitesCheckTheInviteesEmailAndPhone() {
        Institution inst = institution("Invite Rules U");
        User owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        String token = login(owner);

        Map<String, Object> invite = new HashMap<>();
        invite.put("email", "new.person@example");
        invite.put("targetRole", "FACILITATOR");
        invite.put("firstName", "New");
        invite.put("lastName", "Person");
        assertThat(call("POST", "/api/auth/staff-invites", invite, token).message()).isEqualTo(ValidationPatterns.EMAIL_MESSAGE);

        invite.put("email", freshEmail());
        invite.put("phoneNumber", "0801");
        assertThat(call("POST", "/api/auth/staff-invites", invite, token).message()).isEqualTo(ValidationPatterns.PHONE_MESSAGE);

        invite.put("phoneNumber", "08012345678"); // local format is accepted
        Resp ok = call("POST", "/api/auth/staff-invites", invite, token);
        assertThat(ok.status()).as(ok.toString()).isEqualTo(201);
    }

    @Test
    void changingAPasswordNeedsAStrongOneButSigningInDoesnt() {
        User u = user("pw");
        member(u, institution("Password Rules U"), Role.STUDENT);
        // Existing passwords aren't re-checked at sign-in — PASSWORD is fine, and so would an old weak one be.
        String token = login(u);

        Resp weak = call("POST", "/api/auth/change-password",
                Map.of("currentPassword", PASSWORD, "newPassword", "alllowercase"), token);
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.message()).isEqualTo(ValidationPatterns.PASSWORD_MESSAGE);

        Resp strong = call("POST", "/api/auth/change-password",
                Map.of("currentPassword", PASSWORD, "newPassword", "N3w-Password!"), token);
        assertThat(strong.status()).isBetween(200, 204);
    }
}
