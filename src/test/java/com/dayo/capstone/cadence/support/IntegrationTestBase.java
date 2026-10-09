package com.dayo.capstone.cadence.support;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.admin.repository.AdminRoleRepository;
import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.ExtensionRequest;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.repository.ExtensionRequestRepository;
import com.dayo.capstone.cadence.assignment.repository.SubmissionRepository;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.attendance.repository.SessionRepository;
import com.dayo.capstone.cadence.audit.repository.AuditLogRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.repository.CohortRepository;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.institution.repository.SubjectRepository;
import com.dayo.capstone.cadence.security.repository.StaffInviteTokenRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Full-stack integration tests: the real app on a random port, real HTTP
 * through the real JWT filter, and an in-memory H2 database (PostgreSQL mode).
 * The RSA signing keys are generated per run and never committed.
 *
 * Every fixture uses unique names/emails, so tests don't depend on each other
 * even though they share one application context and database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Test-Password-1!";
    private static final Path KEY_DIR = generateKeys();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.private-key-path", () -> "file:" + KEY_DIR.resolve("private_key.pem"));
        registry.add("app.jwt.public-key-path", () -> "file:" + KEY_DIR.resolve("public_key.pem"));
    }

    @LocalServerPort protected int port;
    @Autowired protected InstitutionRepository institutionRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected MembershipRepository membershipRepository;
    @Autowired protected CohortRepository cohortRepository;
    @Autowired protected SubjectRepository subjectRepository;
    @Autowired protected SessionRepository sessionRepository;
    @Autowired protected ResumptionSessionRepository resumptionSessionRepository;
    @Autowired protected AssignmentRepository assignmentRepository;
    @Autowired protected SubmissionRepository submissionRepository;
    @Autowired protected ExtensionRequestRepository extensionRequestRepository;
    @Autowired protected AdminRoleRepository adminRoleRepository;
    @Autowired protected UserAdminRoleAssignmentRepository assignmentRepositoryForAdmins;
    @Autowired protected StaffInviteTokenRepository inviteRepository;
    @Autowired protected AuditLogRepository auditLogRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    // ── Fixtures ───────────────────────────────────────────────────────────

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Institution institution(String name) {
        Institution i = new Institution();
        i.setName(name);
        i.setSlug(unique("inst"));
        return institutionRepository.save(i);
    }

    protected User user(String label) {
        User u = new User();
        u.setFirstName(label);
        u.setLastName("Tester");
        u.setEmail(unique(label).toLowerCase() + "@test.cadence");
        u.setPhoneNumber("+234" + String.format("%010d", (long) (Math.random() * 1_000_000_0000L)));
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setEmailVerified(true);
        u.setAccountPending(false);
        u.setConsentGiven(true);
        return userRepository.save(u);
    }

    protected Membership member(User user, Institution institution, Role... roles) {
        return membershipRepository.save(Membership.of(user, institution, EnumSet.of(roles[0], roles)));
    }

    protected Cohort cohort(Institution institution) {
        Cohort c = new Cohort();
        c.setInstitution(institution);
        c.setName(unique("cohort"));
        c.setStartDate(LocalDate.now());
        return cohortRepository.save(c);
    }

    protected Subject subject(Institution institution) {
        Subject s = new Subject();
        s.setInstitution(institution);
        s.setName(unique("subject"));
        return subjectRepository.save(s);
    }

    protected Session session(Cohort cohort, Subject subject, User facilitator) {
        Session s = new Session();
        s.setCohort(cohort);
        s.setSubject(subject);
        s.setFacilitator(facilitator);
        s.setSessionCode(unique("code"));
        s.setQrToken(UUID.randomUUID().toString());
        s.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return sessionRepository.save(s);
    }

    protected ExtensionRequest extensionRequest(Cohort cohort, Subject subject, User facilitator, User student) {
        Assignment a = new Assignment();
        a.setCohort(cohort);
        a.setSubject(subject);
        a.setFacilitator(facilitator);
        a.setTitle(unique("assignment"));
        a.setDeadline(LocalDateTime.now().plusDays(3));
        a = assignmentRepository.save(a);
        Submission sub = new Submission();
        sub.setAssignment(a);
        sub.setStudent(student);
        sub = submissionRepository.save(sub);
        ExtensionRequest e = new ExtensionRequest();
        e.setSubmission(sub);
        e.setStudent(student);
        e.setRequestedNewDeadline(LocalDateTime.now().plusDays(5));
        e.setReason("test");
        return extensionRequestRepository.save(e);
    }

    protected ResumptionSession resumptionSession(Cohort cohort) {
        return resumptionSession(cohort, LocalDate.now());
    }

    protected ResumptionSession resumptionSession(Cohort cohort, LocalDate date) {
        ResumptionSession r = new ResumptionSession();
        r.setCohort(cohort);
        r.setResumptionDate(date);
        r.setResumptionCode(unique("code"));
        r.setQrToken(UUID.randomUUID().toString());
        r.setOpensAt(LocalDateTime.now().minusMinutes(5));
        r.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return resumptionSessionRepository.save(r);
    }

    /** An admin role in this institution with the given permissions, granted institution-wide. */
    protected void grant(User user, Institution institution, Permission... permissions) {
        grant(user, institution, null, permissions);
    }

    /** As above, scoped to one cohort when {@code cohort} is non-null. */
    protected void grant(User user, Institution institution, Cohort cohort, Permission... permissions) {
        AdminRole role = new AdminRole();
        role.setInstitution(institution);
        role.setName(unique("role"));
        role.setPermissions(Set.of(permissions));
        role = adminRoleRepository.save(role);
        UserAdminRoleAssignment a = new UserAdminRoleAssignment();
        a.setUser(user);
        a.setAdminRole(role);
        a.setCohort(cohort);
        a.setAssignedAt(LocalDateTime.now());
        assignmentRepositoryForAdmins.save(a);
    }

    // ── HTTP ───────────────────────────────────────────────────────────────

    public record Resp(int status, JsonNode body) {
        public String text(String field) {
            JsonNode n = body == null ? null : body.get(field);
            return n == null || n.isNull() ? null : n.asText();
        }
        public String message() { return text("message"); }
    }

    /** Signs in and returns the access token (lands in the user's default institution). */
    protected String login(User user) {
        Resp r = call("POST", "/api/auth/login", Map.of("identifier", user.getEmail(), "password", PASSWORD), null);
        if (r.status() != 200) throw new IllegalStateException("login failed: " + r);
        return r.text("accessToken");
    }

    /** Moves a session into another institution and returns the new access token. */
    protected Resp switchTo(String token, Institution institution) {
        return call("POST", "/api/auth/switch-institution", Map.of("institutionPublicId", institution.getPublicId().toString()), token);
    }

    protected String tokenIn(User user, Institution institution) {
        Resp r = switchTo(login(user), institution);
        if (r.status() != 200) throw new IllegalStateException("switch failed: " + r);
        return r.text("accessToken");
    }

    protected Resp get(String path, String token) { return call("GET", path, null, token); }

    protected Resp call(String method, String path, Object body, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "cadence-tests");
            if (token != null) b.header("Authorization", "Bearer " + token);
            String payload = body == null ? "" : JSON.writeValueAsString(body);
            b.method(method, body == null && !method.equals("POST") && !method.equals("PUT") && !method.equals("PATCH")
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = res.body() == null || res.body().isBlank() ? null : JSON.readTree(res.body());
            return new Resp(res.statusCode(), node);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Keys ───────────────────────────────────────────────────────────────

    private static Path generateKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            Path dir = Files.createTempDirectory("cadence-test-keys");
            dir.toFile().deleteOnExit();
            Files.writeString(dir.resolve("private_key.pem"), pem("PRIVATE KEY", kp.getPrivate().getEncoded()));
            Files.writeString(dir.resolve("public_key.pem"), pem("PUBLIC KEY", kp.getPublic().getEncoded()));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate test JWT keys", e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
