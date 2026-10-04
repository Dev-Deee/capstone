package com.dayo.capstone.cadence.attendance;

import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.service.TotpService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.security.repository.WebAuthnCredentialRepository;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.support.SoftwareAuthenticator;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD Section 6.12 — after the code is accepted, a webAuthnCapable student must
 * confirm on-device before the check-in counts; everyone else is unchanged.
 * Assertions are signed by a real (software) key and checked by the real
 * WebAuthn verification, at both tiers.
 */
class BiometricCheckInTest extends IntegrationTestBase {

    @Autowired TotpService totpService;
    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired WebAuthnCredentialRepository credentialRepository;
    @Autowired AttendanceRecordRepository attendanceRecordRepository;
    @Autowired ResumptionAttendanceRecordRepository resumptionRecordRepository;
    @Value("${app.webauthn.origin}") String origin;
    @Value("${app.webauthn.rp-id}") String rpId;

    Institution inst;
    Cohort cohort;
    User facilitator;
    Session session;
    ResumptionSession resumption;

    @BeforeEach
    void setUp() {
        inst = institution("Biometric Institute");
        cohort = cohort(inst);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        session = session(cohort, subject(inst), facilitator);
        session.setCodeSecret(totpService.generateSecret());
        session = sessionRepository.save(session);
        resumption = resumptionSession(cohort);
        resumption.setCodeSecret(totpService.generateSecret());
        resumption = resumptionSessionRepository.save(resumption);
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private User student() {
        User s = user("stu");
        member(s, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(s);
        e.setCohort(cohort);
        e.setEnrolledAt(LocalDateTime.now().minusDays(1));
        enrollmentRepository.save(e);
        return s;
    }

    /** A student who registered a biometric credential — registration is what sets webAuthnCapable. */
    private User capableStudent(SoftwareAuthenticator device) {
        User s = student();
        credentialRepository.save(device.credentialFor(s));
        s.setWebAuthnCapable(true);
        return userRepository.save(s);
    }

    private Resp checkIn(String token) {
        return call("POST", "/api/attendance",
                Map.of("sessionCode", totpService.currentCode(session.getCodeSecret(), TotpService.PERIOD_SECONDS)), token);
    }

    private Resp resumptionCheckIn(String token) {
        return call("POST", "/api/resumption-attendance",
                Map.of("resumptionCode", totpService.currentCode(resumption.getCodeSecret(), TotpService.PERIOD_SECONDS)), token);
    }

    private static String challengeOf(Resp r) {
        return r.body().get("biometric").get("challenge").asText();
    }

    private Map<String, String> signed(SoftwareAuthenticator device, String challenge) {
        return device.assertion(challenge, rpId, origin, true);
    }

    private Resp confirm(String base, Resp pending, Map<String, String> assertion, String token) {
        return call("POST", base + "/" + pending.text("publicId") + "/biometric", assertion, token);
    }

    private Resp reissue(Resp pending, String token) {
        return call("POST", "/api/attendance/" + pending.text("publicId") + "/biometric/challenge", null, token);
    }

    private AttendanceRecord record(Resp r) {
        return attendanceRecordRepository.findAll().stream()
                .filter(a -> a.getPublicId().toString().equals(r.text("publicId"))).findFirst().orElseThrow();
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void studentWithoutBiometricsIsCheckedInByCodeAloneAsBefore() {
        String token = login(student());
        Resp r = checkIn(token);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.text("status")).isEqualTo("PRESENT");
        assertThat(r.text("webAuthnVerified")).isNull();
        assertThat(r.text("biometric")).isNull();

        Resp res = resumptionCheckIn(token);
        assertThat(res.status()).isEqualTo(201);
        assertThat(res.text("status")).isEqualTo("PRESENT");
        assertThat(res.text("biometric")).isNull();
    }

    @Test
    void capableStudentIsPendingUntilTheirDeviceConfirms() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));

        Resp pending = checkIn(token);
        assertThat(pending.status()).isEqualTo(201);
        assertThat(pending.text("status")).isEqualTo("PENDING_BIOMETRIC");
        assertThat(pending.text("webAuthnVerified")).isEqualTo("false");
        assertThat(pending.body().get("biometric").get("credentialIds").get(0).asText()).isEqualTo(device.credentialId());
        assertThat(pending.body().get("biometric").get("rpId").asText()).isEqualTo(rpId);
        assertThat(record(pending).getStatus().countsAsAttended()).isFalse();

        Resp done = confirm("/api/attendance", pending, signed(device, challengeOf(pending)), token);
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.text("status")).isEqualTo("PRESENT");
        assertThat(done.text("webAuthnVerified")).isEqualTo("true");
        assertThat(record(pending).getWebAuthnVerified()).isTrue();
        assertThat(record(pending).getStatus().countsAsAttended()).isTrue();

        // Complete — a second confirmation or code entry changes nothing.
        assertThat(confirm("/api/attendance", pending, signed(device, challengeOf(pending)), token).status())
                .isEqualTo(400);
        assertThat(checkIn(token).status()).isEqualTo(400);
    }

    @Test
    void sameRuleAppliesToCampusResumption() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));

        Resp pending = resumptionCheckIn(token);
        assertThat(pending.status()).isEqualTo(201);
        assertThat(pending.text("status")).isEqualTo("PENDING_BIOMETRIC");

        Resp done = confirm("/api/resumption-attendance", pending, signed(device, challengeOf(pending)), token);
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.text("status")).isEqualTo("PRESENT");
        ResumptionAttendanceRecord saved = resumptionRecordRepository.findAll().stream()
                .filter(r -> r.getPublicId().toString().equals(pending.text("publicId"))).findFirst().orElseThrow();
        assertThat(saved.getWebAuthnVerified()).isTrue();
    }

    @Test
    void cancelledPromptLeavesItPendingForTheFacilitatorsManualOverride() {
        User student = capableStudent(new SoftwareAuthenticator());
        Resp pending = checkIn(login(student));
        // The student dismisses the prompt: nothing more is sent, nothing completes.
        assertThat(record(pending).getStatus().countsAsAttended()).isFalse();

        // The roster shows the facilitator the check-in is waiting on them.
        String facToken = login(facilitator);
        Resp roster = get("/api/sessions/" + session.getPublicId() + "/attendance", facToken);
        assertThat(roster.body().findValuesAsText("status")).contains("PENDING_BIOMETRIC");

        // Overriding to the pending state itself is not allowed…
        String path = "/api/sessions/" + session.getPublicId() + "/attendance/" + student.getPublicId();
        assertThat(call("PUT", path, Map.of("status", "PENDING_BIOMETRIC", "reason", "x"), facToken).status())
                .isEqualTo(400);
        // …the existing override resolves it, and the record still says biometrics never confirmed.
        Resp overridden = call("PUT", path, Map.of("status", "PRESENT", "reason", "Fingerprint sensor broken"), facToken);
        assertThat(overridden.status()).isEqualTo(200);
        AttendanceRecord r = record(pending);
        assertThat(r.getStatus().name()).isEqualTo("PRESENT");
        assertThat(r.getVerificationMethod().name()).isEqualTo("MANUAL_OVERRIDE");
        assertThat(r.getOverrideReason()).isEqualTo("Fingerprint sensor broken");
        assertThat(r.getWebAuthnVerified()).isFalse();
    }

    @Test
    void failedAssertionsAreRejectedAndNeverTreatedAsVerified() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));
        Resp pending = checkIn(token);

        // A different key claiming the student's credential ID (forged signature).
        Map<String, String> forged = new HashMap<>(signed(new SoftwareAuthenticator(), challengeOf(pending)));
        forged.put("credentialId", device.credentialId());
        assertThat(confirm("/api/attendance", pending, forged, token).status()).isEqualTo(400);

        // The device only saw a tap — no fingerprint / face match (UV flag unset).
        String c2 = reissue(pending, token).text("challenge");
        assertThat(confirm("/api/attendance", pending, device.assertion(c2, rpId, origin, false), token).status())
                .isEqualTo(400);

        // Another student's genuine credential.
        SoftwareAuthenticator otherDevice = new SoftwareAuthenticator();
        capableStudent(otherDevice);
        String c3 = reissue(pending, token).text("challenge");
        assertThat(confirm("/api/attendance", pending, signed(otherDevice, c3), token).status()).isEqualTo(400);

        // Replaying a challenge that was already consumed.
        assertThat(confirm("/api/attendance", pending, signed(device, c3), token).status()).isEqualTo(400);

        AttendanceRecord r = record(pending);
        assertThat(r.getStatus().name()).isEqualTo("PENDING_BIOMETRIC");
        assertThat(r.getWebAuthnVerified()).isFalse();
    }

    @Test
    void networkDropMidFlowResumesWithoutReEnteringTheCode() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));
        Resp pending = checkIn(token);
        // The connection drops: the challenge in that response is never used.

        // Coming back, the student gets a fresh challenge for the same record…
        Resp fresh = reissue(pending, token);
        assertThat(fresh.status()).isEqualTo(200);
        assertThat(confirm("/api/attendance", pending, signed(device, fresh.text("challenge")), token).status())
                .isEqualTo(200);
        assertThat(record(pending).getStatus().name()).isEqualTo("PRESENT");
    }

    @Test
    void reEnteringTheCodeResumesTheSamePendingCheckIn() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));
        Resp pending = checkIn(token);

        Resp again = checkIn(token);
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.text("publicId")).isEqualTo(pending.text("publicId"));
        assertThat(again.text("status")).isEqualTo("PENDING_BIOMETRIC");

        assertThat(confirm("/api/attendance", pending, signed(device, challengeOf(again)), token).status())
                .isEqualTo(200);
    }

    @Test
    void closingTheSessionMidFlowHonoursAConfirmationWithinTheGracePeriod() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));
        Resp pending = checkIn(token);

        // The facilitator closes the session while the fingerprint prompt is up.
        session.setOpen(false);
        session.setClosedAt(LocalDateTime.now().minusSeconds(20));
        sessionRepository.save(session);

        Resp done = confirm("/api/attendance", pending, signed(device, challengeOf(pending)), token);
        assertThat(done.status()).isEqualTo(200);
        assertThat(done.text("status")).isEqualTo("PRESENT");

        // A new check-in after closure is still refused outright (the hard boundary).
        assertThat(checkIn(login(capableStudent(new SoftwareAuthenticator()))).status()).isEqualTo(400);
    }

    @Test
    void confirmationLongAfterClosureIsLeftToTheFacilitator() {
        SoftwareAuthenticator device = new SoftwareAuthenticator();
        String token = login(capableStudent(device));
        Resp pending = checkIn(token);

        session.setOpen(false);
        session.setClosedAt(LocalDateTime.now().minusMinutes(10));
        sessionRepository.save(session);

        Resp late = confirm("/api/attendance", pending, signed(device, challengeOf(pending)), token);
        assertThat(late.status()).isEqualTo(400);
        assertThat(record(pending).getStatus().name()).isEqualTo("PENDING_BIOMETRIC");
    }

    @Test
    void aStudentCannotTouchAnotherStudentsPendingCheckIn() {
        String token = login(capableStudent(new SoftwareAuthenticator()));
        Resp pending = checkIn(token);
        assertThat(reissue(pending, login(student())).status()).isEqualTo(404);
        assertThat(call("POST", "/api/attendance/" + UUID.randomUUID() + "/biometric/challenge", null, token).status())
                .isEqualTo(404);
    }
}
