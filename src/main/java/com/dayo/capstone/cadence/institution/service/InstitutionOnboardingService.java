package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.common.exception.ConflictException;
import com.dayo.capstone.cadence.institution.dto.request.FoundInstitutionRequest;
import com.dayo.capstone.cadence.institution.dto.request.InstitutionSignupRequest;
import com.dayo.capstone.cadence.institution.dto.response.HandleAvailabilityResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionOnboardingService {

    private static final Pattern HANDLE = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int HANDLE_MIN = 3;
    private static final int HANDLE_MAX = 40;
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
        Institution institution = createInstitution(request.getInstitutionName(), request.getHandle());

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
        Institution institution = createInstitution(request.getName(), request.getHandle());
        membershipService.add(founder, institution, EnumSet.of(Role.SUPER_ADMIN));
        audit(founder, institution);
        return sessionTokenService.issue(founder, institution, userAgent, ipAddress);
    }

    @Transactional(readOnly = true)
    public HandleAvailabilityResponse checkHandle(String rawHandle) {
        String handle = rawHandle == null ? "" : rawHandle.trim().toLowerCase(Locale.ROOT);
        String problem = formatProblem(handle);
        if (problem != null) {
            String suggestion = toHandle(handle);
            return new HandleAvailabilityResponse(handle, false, problem,
                    formatProblem(suggestion) == null ? freeVariant(suggestion) : null);
        }
        if (institutionRepository.existsByHandle(handle)) {
            return new HandleAvailabilityResponse(handle, false, "That address is already taken", freeVariant(handle));
        }
        return new HandleAvailabilityResponse(handle, true, null, null);
    }

    /** "Lagos Tech Academy" → "lagos-tech-academy" (accents stripped, punctuation dropped, max length). */
    public static String toHandle(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (s.length() > HANDLE_MAX) s = s.substring(0, HANDLE_MAX).replaceAll("-+$", "");
        return s;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private Institution createInstitution(String name, String rawHandle) {
        String handle = rawHandle == null ? "" : rawHandle.trim().toLowerCase(Locale.ROOT);
        String problem = formatProblem(handle);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (institutionRepository.existsByHandle(handle)) {
            throw handleTaken(handle);
        }
        Institution institution = new Institution();
        institution.setName(name.trim());
        institution.setHandle(handle);
        try {
            return institutionRepository.saveAndFlush(institution);
        } catch (DataIntegrityViolationException e) {
            // Lost a race for the same handle between the check and the insert.
            throw handleTaken(handle);
        }
    }

    private ConflictException handleTaken(String handle) {
        return new ConflictException("The address '" + handle + "' is already taken. Try '" + freeVariant(handle) + "'.");
    }

    private String formatProblem(String handle) {
        if (handle.length() < HANDLE_MIN || handle.length() > HANDLE_MAX) {
            return "The address must be " + HANDLE_MIN + "–" + HANDLE_MAX + " characters";
        }
        if (!HANDLE.matcher(handle).matches()) {
            return "Use lowercase letters, numbers and single hyphens only";
        }
        if (RESERVED.contains(handle)) {
            return "That address is reserved";
        }
        return null;
    }

    private String freeVariant(String handle) {
        if (handle.isEmpty()) return null;
        if (!institutionRepository.existsByHandle(handle) && !RESERVED.contains(handle)) return handle;
        for (int i = 2; i < 1000; i++) {
            String suffix = "-" + i;
            String base = handle.length() + suffix.length() > HANDLE_MAX ? handle.substring(0, HANDLE_MAX - suffix.length()) : handle;
            String candidate = base.replaceAll("-+$", "") + suffix;
            if (!institutionRepository.existsByHandle(candidate)) return candidate;
        }
        return null;
    }

    private void audit(User founder, Institution institution) {
        auditLogService.log(founder.getPublicId(), "[" + Role.SUPER_ADMIN + "]", AuditAction.INSTITUTION_CREATED,
                "Institution", institution.getPublicId(), null,
                "{\"name\":\"" + institution.getName().replace("\"", "\\\"") + "\",\"handle\":\"" + institution.getHandle() + "\"}");
        log.info("Institution founded: institutionPublicId={}, handle={}, founderPublicId={}",
                institution.getPublicId(), institution.getHandle(), founder.getPublicId());
    }
}
