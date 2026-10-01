package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.admin.repository.AdminRoleRepository;
import com.dayo.capstone.cadence.admin.repository.UserAdminRoleAssignmentRepository;
import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.CohortInviteCode;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.service.CohortInviteCodeService;
import com.dayo.capstone.cadence.cohort.service.CohortEnrollmentService;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.security.config.JwtProperties;
import com.dayo.capstone.cadence.security.dto.request.*;
import com.dayo.capstone.cadence.security.dto.response.ActiveDeviceResponse;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.model.*;
import com.dayo.capstone.cadence.security.repository.*;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final CohortInviteCodeService cohortInviteCodeService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final StaffInviteTokenRepository staffInviteTokenRepository;
    private final PhoneVerificationTokenRepository phoneVerificationTokenRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final UserAdminRoleAssignmentRepository userAdminRoleAssignmentRepository;
    private final CohortService cohortService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AuditLogService auditLogService;
    private final CohortEnrollmentService cohortEnrollmentService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ─────────────────────────────────────────────────────────────────────
    // STUDENT SELF-REGISTRATION (via CohortInviteCode)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already registered: " + request.getPhoneNumber());
        }
        if (!Boolean.TRUE.equals(request.getConsentGiven())) {
            throw new IllegalArgumentException("Consent is required to register");
        }

        CohortInviteCode invite = cohortInviteCodeService.resolveRedeemable(request.getInviteCode());

        User user = new User();
        user.setInstitution(invite.getCohort().getInstitution());
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setInstitutionStudentId(request.getInstitutionStudentId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(Role.STUDENT)); // hardcoded — never derived from request or invite
        user.setConsentGiven(true);
        user.setConsentTimestamp(LocalDateTime.now());
        user.setConsentVersion("v1");
        user.setAccountPending(false); // self-registered students are active immediately
        user.setEmailVerified(false);  // but must verify email before their NEXT login

        User saved = userRepository.save(user);
        cohortEnrollmentService.enroll(saved, invite.getCohort());

        cohortInviteCodeService.markUsed(invite);

        sendEmailVerification(saved);

        log.info("Self-registration successful: userPublicId={}, cohortPublicId={}, role=STUDENT",
                saved.getPublicId(), invite.getCohort().getPublicId());

        return issueTokens(saved, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOGIN / LOGOUT
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        String identifier = request.getIdentifier().trim();

        User user = (identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByPhoneNumber(identifier))
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.isAccountPending()) {
            throw new BadCredentialsException(
                    "Please activate your account using the link sent to your email");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            log.warn("Login attempt on locked account: userPublicId={}", user.getPublicId());
            throw new BadCredentialsException(
                    "Account temporarily locked due to repeated failed attempts. Try again later.");
        }

        if (!user.isEmailVerified()) {
            log.warn("Login attempt on unverified account: userPublicId={}", user.getPublicId());
            throw new BadCredentialsException(
                    "Please verify your email before logging in. Check your inbox for the verification link.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                auditLogService.log(user.getPublicId(), user.getRoles().toString(),
                        AuditAction.ACCOUNT_LOCKED, "User", user.getPublicId());
                log.warn("Account locked after {} failed attempts: userPublicId={}",
                        MAX_FAILED_ATTEMPTS, user.getPublicId());
            }

            userRepository.save(user);
            auditLogService.log(user.getPublicId(), user.getRoles().toString(),
                    AuditAction.LOGIN_FAILED, "User", user.getPublicId());

            log.warn("Failed login attempt for identifier={}", identifier);
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isActive()) {
            log.warn("Login attempt on deactivated account: userPublicId={}", user.getPublicId());
            throw new BadCredentialsException("This account has been deactivated. Contact your administrator.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditLogService.log(user.getPublicId(), user.getRoles().toString(),
                AuditAction.LOGIN, "User", user.getPublicId());

        log.info("Login successful: userPublicId={}", user.getPublicId());
        return issueTokens(user, userAgent, ipAddress);
    }

    @Transactional
    public void logout(String rawAccessToken, String rawRefreshToken) {
        try {
            Claims claims = jwtService.verifyToken(rawAccessToken);
            String jti = jwtService.extractJti(claims);

            BlacklistedToken blacklisted = new BlacklistedToken();
            blacklisted.setJti(jti);
            blacklisted.setExpiresAt(jwtService.extractExpiration(claims).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            blacklistedTokenRepository.save(blacklisted);
        } catch (Exception e) {
            log.debug("Access token already invalid at logout, skipping blacklist: {}", e.getMessage());
        }

        String hash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });

        log.info("Logout completed: refresh token revoked, access token blacklisted");
    }

    @Transactional
    public AuthResponse refreshAccessToken(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        User user = storedToken.getUser();
        if (user.isDeleted() || !user.isActive()) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        storedToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);

        Set<String> roleNames = user.getRoles().stream().map(Role::name).collect(Collectors.toSet());
        String newAccessToken = jwtService.generateAccessToken(user.getPublicId(), roleNames);

        log.info("Access token refreshed: userPublicId={}", user.getPublicId());

        return AuthResponse.builder()
                .userPublicId(user.getPublicId())
                .accessToken(newAccessToken)
                .refreshToken(rawRefreshToken) // same refresh token — not rotated for this pass
                .roles(roleNames)
                .build();
    }
    // ─────────────────────────────────────────────────────────────────────
    // PASSWORD & EMAIL MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void changePassword(UUID userPublicId, ChangePasswordRequest request) {
        User user = userRepository.findByPublicId(userPublicId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("Failed password change attempt: userPublicId={}", userPublicId);
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user, LocalDateTime.now());

        log.info("Password changed and all sessions revoked: userPublicId={}", userPublicId);
    }

    @Transactional
    public void adminChangeEmail(UUID targetUserPublicId, AdminChangeEmailRequest request, UUID actorPublicId) {
        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getNewEmail());
        }

        User user = userRepository.findByPublicId(targetUserPublicId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserPublicId));
        User actor = userRepository.findByPublicId(actorPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorPublicId));
        if (!user.getInstitution().getId().equals(actor.getInstitution().getId())) {
            throw new ResourceNotFoundException("User not found: " + targetUserPublicId);
        }

        String oldEmail = user.getEmail();
        user.setEmail(request.getNewEmail());
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user, LocalDateTime.now());

        auditLogService.log(actorPublicId, actor.getRoles().toString(), AuditAction.UPDATE, "User", targetUserPublicId,
                "{\"email\":\"" + oldEmail + "\"}", "{\"email\":\"" + request.getNewEmail() + "\"}");

        log.info("Admin changed email: userPublicId={}, oldEmail={}, newEmail={}, reason={}",
                targetUserPublicId, oldEmail, request.getNewEmail(), request.getReason());
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail())
                .filter(u -> !u.isDeleted())
                .ifPresent(user -> {
                    String rawToken = jwtService.generateRefreshTokenRaw();

                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUser(user);
                    resetToken.setTokenHash(jwtService.hashToken(rawToken));
                    resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
                    passwordResetTokenRepository.save(resetToken);

                    String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
                    emailService.send(user.getEmail(), "Reset your Cadence password",
                            "Click here to reset your password (valid 30 minutes): " + resetLink);

                    log.info("Password reset requested: userPublicId={}", user.getPublicId());
                });
        // No exception thrown either way — never reveal whether the email exists.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = jwtService.hashToken(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.revokeAllForUser(user, LocalDateTime.now());

        log.info("Password reset completed: userPublicId={}", user.getPublicId());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = jwtService.hashToken(rawToken);

        EmailVerificationToken verification = emailVerificationTokenRepository.findByTokenHash(hash)
                .filter(EmailVerificationToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        User user = verification.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verification.setUsed(true);
        emailVerificationTokenRepository.save(verification);

        log.info("Email verified: userPublicId={}", user.getPublicId());
    }

    private void sendEmailVerification(User user) {
        String rawToken = jwtService.generateRefreshTokenRaw();

        EmailVerificationToken verification = new EmailVerificationToken();
        verification.setUser(user);
        verification.setTokenHash(jwtService.hashToken(rawToken));
        verification.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailVerificationTokenRepository.save(verification);

        String verifyLink = frontendUrl + "/verify-email?token=" + rawToken;
        emailService.send(user.getEmail(), "Verify your Cadence email",
                "Click here to verify your email (valid 24 hours): " + verifyLink);
    }

    // ─────────────────────────────────────────────────────────────────────
    // STAFF INVITE (FACILITATOR / ADMIN — SUPER_ADMIN or scoped ADMIN)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void createStaffInvite(CreateStaffInviteRequest request, Institution institution, User caller) {
        if (request.getTargetRole() != Role.FACILITATOR && request.getTargetRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Staff invites can only target FACILITATOR or ADMIN");
        }

        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            enforceAdminInviteConstraints(request, caller);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        User user = new User();
        user.setInstitution(institution);
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRoles(Set.of(request.getTargetRole()));
        user.setAccountPending(true);
        user.setEmailVerified(true); // clicking the invite link IS the verification
        User saved = userRepository.save(user);

        String rawToken = UUID.randomUUID().toString();

        StaffInviteToken invite = new StaffInviteToken();
        invite.setUser(saved);
        invite.setTokenHash(jwtService.hashToken(rawToken));
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));

        if (request.getTargetRole() == Role.ADMIN) {
            if (request.getAdminRolePublicId() == null) {
                throw new IllegalArgumentException("adminRolePublicId is required when targetRole is ADMIN");
            }
            AdminRole adminRole = adminRoleRepository.findByPublicId(UUID.fromString(request.getAdminRolePublicId()))
                    .orElseThrow(() -> new ResourceNotFoundException("AdminRole not found"));
            invite.setAdminRole(adminRole);

            if (request.getCohortPublicId() != null) {
                invite.setCohort(cohortService.findEntityByPublicId(UUID.fromString(request.getCohortPublicId())));
            }
        }

        staffInviteTokenRepository.save(invite);

        String activationLink = frontendUrl + "/accept-invite?token=" + rawToken;
        emailService.send(saved.getEmail(), "You've been invited to Cadence",
                "Set your password to activate your account (valid 7 days): " + activationLink);

        log.info("Staff invite created: userPublicId={}, targetRole={}", saved.getPublicId(), request.getTargetRole());
    }

    /**
     * Enforces subset-permission and cohort-scope rules when a non-SUPER_ADMIN
     * ADMIN caller is creating a staff invite.
     *
     * Rules:
     *  - Inviting FACILITATOR: no extra permission check.
     *  - Inviting ADMIN: the target AdminRole's permission set must be a strict
     *    subset of the union of permissions from all of the caller's active
     *    role-assignments whose AdminRole is also active.  If the target invite
     *    specifies a cohort, the caller must hold at least one active assignment
     *    that covers that cohort (assignment.cohort == null means institution-wide).
     */
    private void enforceAdminInviteConstraints(CreateStaffInviteRequest request, User caller) {
        if (request.getTargetRole() == Role.FACILITATOR) {
            // ADMIN can freely invite facilitators — no further check.
            return;
        }

        // --- target is ADMIN: load and validate caller's active assignments ---
        List<UserAdminRoleAssignment> callerAssignments = userAdminRoleAssignmentRepository
                .findByUserAndIsActiveTrue(caller)
                .stream()
                .filter(a -> a.getAdminRole().isActive())
                .collect(Collectors.toList());

        if (callerAssignments.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Caller has no active admin role assignments");
        }

        // Union permissions across all valid assignments.
        Set<com.dayo.capstone.cadence.common.enums.Permission> callerPermissions = callerAssignments.stream()
                .flatMap(a -> a.getAdminRole().getPermissions().stream())
                .collect(Collectors.toSet());

        // Load the target AdminRole.
        AdminRole targetRole = adminRoleRepository
                .findByPublicId(UUID.fromString(request.getAdminRolePublicId()))
                .orElseThrow(() -> new ResourceNotFoundException("AdminRole not found"));

        // Subset check: every permission the target role has must also be in caller's union.
        if (!callerPermissions.containsAll(targetRole.getPermissions())) {
            Set<com.dayo.capstone.cadence.common.enums.Permission> excess = new java.util.HashSet<>(targetRole.getPermissions());
            excess.removeAll(callerPermissions);
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot grant permissions not held by caller: " + excess);
        }

        // Cohort-scope check: if the invite targets a specific cohort, the caller
        // must have at least one assignment covering that cohort or the whole institution.
        if (request.getCohortPublicId() != null) {
            UUID targetCohortId = UUID.fromString(request.getCohortPublicId());
            boolean callerCoversScope = callerAssignments.stream()
                    .anyMatch(a -> a.getCohort() == null
                            || a.getCohort().getPublicId().equals(targetCohortId));
            if (!callerCoversScope) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Caller's assignments do not cover the requested cohort scope");
            }
        }
    }

    @Transactional
    public void acceptStaffInvite(AcceptStaffInviteRequest request) {
        String hash = jwtService.hashToken(request.getToken());

        StaffInviteToken invite = staffInviteTokenRepository.findByTokenHash(hash)
                .filter(StaffInviteToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite token"));

        User user = invite.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountPending(false);
        userRepository.save(user);

        if (invite.getAdminRole() != null) {
            UserAdminRoleAssignment assignment = new UserAdminRoleAssignment();
            assignment.setUser(user);
            assignment.setAdminRole(invite.getAdminRole());
            assignment.setCohort(invite.getCohort());
            assignment.setAssignedAt(LocalDateTime.now());
            userAdminRoleAssignmentRepository.save(assignment);
        }

        invite.setUsed(true);
        staffInviteTokenRepository.save(invite);

        log.info("Staff invite accepted, account activated: userPublicId={}", user.getPublicId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // EMAIL OTP (6-digit code, parallel to existing link-based flow)
    // ─────────────────────────────────────────────────────────────────────

    // Same ifPresent pattern as requestPasswordReset — never reveals
    // whether the email address is registered.
    @Transactional
    public void sendEmailVerificationCode(String email) {
        userRepository.findByEmail(email)
                .filter(u -> !u.isDeleted())
                .ifPresent(user -> {
                    // Invalidate any existing tokens for this user so old codes
                    // cannot be used after a new one is issued, and the unique
                    // constraint on token_hash can never be violated by a repeat
                    // code for the same user.
                    emailVerificationTokenRepository.findByUser(user).forEach(t -> {
                        t.setUsed(true);
                        emailVerificationTokenRepository.save(t);
                    });

                    String code = generateOtpCode();

                    EmailVerificationToken token = new EmailVerificationToken();
                    token.setUser(user);
                    // Salt with userPublicId so two users with the same 6-digit
                    // code produce different stored hashes.
                    token.setTokenHash(jwtService.hashToken(user.getPublicId() + ":" + code));
                    token.setExpiresAt(LocalDateTime.now().plusHours(24));
                    emailVerificationTokenRepository.save(token);

                    emailService.send(user.getEmail(),
                            "Your Cadence email verification code",
                            "Your Cadence verification code is: " + code + " — valid for 24 hours.");

                    log.info("Email verification code sent: userPublicId={}", user.getPublicId());
                });
    }

    @Transactional
    public void verifyEmailCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));

        String hash = jwtService.hashToken(user.getPublicId() + ":" + code);

        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .filter(EmailVerificationToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));

        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        log.info("Email verified via code: userPublicId={}", user.getPublicId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // PHONE OTP (authenticated — user verifies their own number)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void sendPhoneOtp(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("No phone number on file for this account");
        }

        // Invalidate existing tokens before issuing a new one.
        phoneVerificationTokenRepository.findByUser(user).forEach(t -> {
            t.setUsed(true);
            phoneVerificationTokenRepository.save(t);
        });

        String code = generateOtpCode();

        PhoneVerificationToken token = new PhoneVerificationToken();
        token.setUser(user);
        token.setTokenHash(jwtService.hashToken(user.getPublicId() + ":" + code));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        phoneVerificationTokenRepository.save(token);

        smsService.sendOtp(user.getPhoneNumber(), code);

        log.info("Phone OTP sent: userPublicId={}", user.getPublicId());
    }

    @Transactional
    public void verifyPhoneOtp(UUID userPublicId, String code) {
        User user = userRepository.findByPublicId(userPublicId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));

        String hash = jwtService.hashToken(user.getPublicId() + ":" + code);

        PhoneVerificationToken token = phoneVerificationTokenRepository.findByTokenHash(hash)
                .filter(PhoneVerificationToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));

        user.setPhoneVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        phoneVerificationTokenRepository.save(token);

        log.info("Phone verified via OTP: userPublicId={}", user.getPublicId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // ACTIVE DEVICES
    // ─────────────────────────────────────────────────────────────────────

    public List<ActiveDeviceResponse> getActiveDevices(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));

        return refreshTokenRepository.findByUserAndRevokedFalseAndExpiresAtAfter(user, LocalDateTime.now())
                .stream()
                .map(token -> ActiveDeviceResponse.builder()
                        .publicId(token.getPublicId())
                        .userAgent(token.getUserAgent())
                        .ipAddress(token.getIpAddress())
                        .lastUsedAt(token.getLastUsedAt())
                        .expiresAt(token.getExpiresAt())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeDevice(UUID userPublicId, UUID refreshTokenPublicId) {
        RefreshToken token = refreshTokenRepository.findByPublicId(refreshTokenPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + refreshTokenPublicId));

        if (!token.getUser().getPublicId().equals(userPublicId)) {
            throw new ResourceNotFoundException("Device not found: " + refreshTokenPublicId);
        }

        token.setRevoked(true);
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        log.info("Device revoked: userPublicId={}, devicePublicId={}", userPublicId, refreshTokenPublicId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // SHARED HELPERS
    // ─────────────────────────────────────────────────────────────────────

    // 6-digit zero-padded numeric code: SecureRandom, always exactly 6
    // digits (e.g. "042917", never "42917").
    private String generateOtpCode() {
        int code = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private AuthResponse issueTokens(User user, String userAgent, String ipAddress) {
        Set<String> roleNames = user.getRoles().stream().map(Role::name).collect(Collectors.toSet());

        // Revoke any existing active session from this same browser/device before
        // issuing a new one — prevents unbounded accumulation of "Active" devices
        // every time the same browser logs in again.
        refreshTokenRepository.findByUserAndUserAgentAndRevokedFalse(user, userAgent)
                .forEach(existing -> {
                    existing.setRevoked(true);
                    existing.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(existing);
                });

        String accessToken = jwtService.generateAccessToken(user.getPublicId(), roleNames);
        String rawRefreshToken = jwtService.generateRefreshTokenRaw();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(jwtService.hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpiryMs() / 1000));
        refreshToken.setUserAgent(userAgent);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .userPublicId(user.getPublicId())
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .roles(roleNames)
                .build();
    }
}