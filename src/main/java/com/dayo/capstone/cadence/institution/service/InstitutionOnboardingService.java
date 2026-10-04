package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.common.exception.ConflictException;
import com.dayo.capstone.cadence.institution.dto.request.FoundInstitutionRequest;
import com.dayo.capstone.cadence.institution.dto.request.InstitutionSignupRequest;
import com.dayo.capstone.cadence.institution.dto.response.SlugAvailabilityResponse;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.service.AuthService;
import com.dayo.capstone.cadence.security.service.SessionTokenService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Self-serve institution sign-up. Instant — no approval gate — and whoever
 * founds an institution becomes its SUPER_ADMIN (owner of that institution
 * only; platform-wide reach is never granted here).
 *
 * Slugs: chosen by the founder, validated, and immutable afterwards. A taken
 * slug is rejected with a clear message and a free suggestion — never silently
 * altered and never surfaced as a raw database error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionOnboardingService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int SLUG_MIN = 3;
    private static final int SLUG_MAX = 40;
    // Words that would clash with routes or impersonate the platform.
    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "app", "www", "login", "logout", "signup", "sign-up", "register", "dashboard",
            "cadence", "platform", "support", "help", "static", "assets", "invite", "invites", "settings");

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final SessionTokenService sessionTokenService;
    private final AuditLogService auditLogService;

    /** Brand-new person + brand-new institution, in one transaction. */
    @Transactional
    public AuthResponse signUp(InstitutionSignupRequest request, String userAgent, String ipAddress) {
        if (!Boolean.TRUE.equals(request.getConsentGiven())) {
            throw new IllegalArgumentException("Consent is required to sign up");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("An account with this email already exists. "
                    + "Sign in, then create your institution from your account.");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber().trim())) {
            throw new ConflictException("This phone number is already registered");
        }
        Institution institution = createInstitution(request.getInstitutionName(), request.getSlug());

        User founder = new User();
        founder.setFirstName(request.getFirstName().trim());
        founder.setLastName(request.getLastName().trim());
        founder.setEmail(email);
        founder.setPhoneNumber(request.getPhoneNumber().trim());
        founder.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        founder.setConsentGiven(true);
        founder.setConsentTimestamp(LocalDateTime.now());
        founder.setConsentVersion("v1");
        founder.setAccountPending(false);
        founder.setEmailVerified(false); // signed in now; must verify before their next login
        founder = userRepository.save(founder);

        membershipService.add(founder, institution, EnumSet.of(Role.SUPER_ADMIN));
        authService.sendEmailVerification(founder);
        audit(founder, institution);
        return sessionTokenService.issue(founder, institution, userAgent, ipAddress);
    }

    /**
     * An existing user founds another institution. Their other memberships —
     * whatever roles they hold there — are untouched; this adds a new
     * SUPER_ADMIN membership and switches their session into it.
     */
    @Transactional
    public AuthResponse found(FoundInstitutionRequest request, User founder, String userAgent, String ipAddress) {
        Institution institution = createInstitution(request.getName(), request.getSlug());
        membershipService.add(founder, institution, EnumSet.of(Role.SUPER_ADMIN));
        audit(founder, institution);
        return sessionTokenService.issue(founder, institution, userAgent, ipAddress);
    }

    @Transactional(readOnly = true)
    public SlugAvailabilityResponse checkSlug(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT);
        String problem = formatProblem(slug);
        if (problem != null) {
            String suggestion = slugify(slug);
            return new SlugAvailabilityResponse(slug, false, problem,
                    formatProblem(suggestion) == null ? freeVariant(suggestion) : null);
        }
        if (institutionRepository.existsBySlug(slug)) {
            return new SlugAvailabilityResponse(slug, false, "That address is already taken", freeVariant(slug));
        }
        return new SlugAvailabilityResponse(slug, true, null, null);
    }

    /** "Lagos Tech Academy" → "lagos-tech-academy" (accents stripped, punctuation dropped, max length). */
    public static String slugify(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (s.length() > SLUG_MAX) s = s.substring(0, SLUG_MAX).replaceAll("-+$", "");
        return s;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private Institution createInstitution(String name, String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT);
        String problem = formatProblem(slug);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (institutionRepository.existsBySlug(slug)) {
            throw slugTaken(slug);
        }
        Institution institution = new Institution();
        institution.setName(name.trim());
        institution.setSlug(slug);
        try {
            return institutionRepository.saveAndFlush(institution);
        } catch (DataIntegrityViolationException e) {
            // Lost a race for the same slug between the check and the insert.
            throw slugTaken(slug);
        }
    }

    private ConflictException slugTaken(String slug) {
        return new ConflictException("The address '" + slug + "' is already taken. Try '" + freeVariant(slug) + "'.");
    }

    private String formatProblem(String slug) {
        if (slug.length() < SLUG_MIN || slug.length() > SLUG_MAX) {
            return "The address must be " + SLUG_MIN + "–" + SLUG_MAX + " characters";
        }
        if (!SLUG.matcher(slug).matches()) {
            return "Use lowercase letters, numbers and single hyphens only";
        }
        if (RESERVED.contains(slug)) {
            return "That address is reserved";
        }
        return null;
    }

    private String freeVariant(String slug) {
        if (slug.isEmpty()) return null;
        if (!institutionRepository.existsBySlug(slug) && !RESERVED.contains(slug)) return slug;
        for (int i = 2; i < 1000; i++) {
            String suffix = "-" + i;
            String base = slug.length() + suffix.length() > SLUG_MAX ? slug.substring(0, SLUG_MAX - suffix.length()) : slug;
            String candidate = base.replaceAll("-+$", "") + suffix;
            if (!institutionRepository.existsBySlug(candidate)) return candidate;
        }
        return null;
    }

    private void audit(User founder, Institution institution) {
        auditLogService.log(founder.getPublicId(), "[" + Role.SUPER_ADMIN + "]", AuditAction.INSTITUTION_CREATED,
                "Institution", institution.getPublicId(), null,
                "{\"name\":\"" + institution.getName().replace("\"", "\\\"") + "\",\"slug\":\"" + institution.getSlug() + "\"}");
        log.info("Institution founded: institutionPublicId={}, slug={}, founderPublicId={}",
                institution.getPublicId(), institution.getSlug(), founder.getPublicId());
    }
}
