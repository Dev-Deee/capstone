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
import com.dayo.capstone.cadence.cohort.repository.CohortInviteCodeRepository;
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
    private final CohortInviteCodeRepository cohortInviteCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final StaffInviteTokenRepository staffInviteTokenRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final UserAdminRoleAssignmentRepository userAdminRoleAssignmentRepository;
    private final CohortService cohortService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

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

        CohortInviteCode invite = cohortInviteCodeRepository.findByCode(request.getInviteCode())
                .filter(CohortInviteCode::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite code"));

        if (!invite.getCohort().isActive()) {
            throw new IllegalArgumentException("This cohort is no longer active");
        }
        if (!invite.getCohort().getInstitution().isActive()) {
            throw new IllegalArgumentException("This institution is no longer active");
        }

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

        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setStudent(saved);
        enrollment.setCohort(invite.getCohort());
        enrollment.setEnrolledAt(LocalDateTime.now());
        cohortEnrollmentRepository.save(enrollment);

        invite.setUseCount(invite.getUseCount() + 1);
        cohortInviteCodeRepository.save(invite);

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

        String oldEmail = user.getEmail();
        user.setEmail(request.getNewEmail());
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user, LocalDateTime.now());

        auditLogService.log(actorPublicId, "ADMIN", AuditAction.UPDATE, "User", targetUserPublicId,
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
    // STAFF INVITE (FACILITATOR / ADMIN — SUPER_ADMIN only, enforced in controller)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void createStaffInvite(CreateStaffInviteRequest request, Institution institution) {
        if (request.getTargetRole() != Role.FACILITATOR && request.getTargetRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Staff invites can only target FACILITATOR or ADMIN");
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

        String activationLink = frontendUrl + "/activate?token=" + rawToken;
        emailService.send(saved.getEmail(), "You've been invited to Cadence",
                "Set your password to activate your account (valid 7 days): " + activationLink);

        log.info("Staff invite created: userPublicId={}, targetRole={}", saved.getPublicId(), request.getTargetRole());
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
    // SHARED HELPER
    // ─────────────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user, String userAgent, String ipAddress) {
        Set<String> roleNames = user.getRoles().stream().map(Role::name).collect(Collectors.toSet());

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