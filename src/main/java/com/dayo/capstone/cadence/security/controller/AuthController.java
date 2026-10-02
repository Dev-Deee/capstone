package com.dayo.capstone.cadence.security.controller;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.security.dto.request.*;
import com.dayo.capstone.cadence.security.dto.response.ActiveDeviceResponse;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.service.AuthService;
import com.dayo.capstone.cadence.security.service.InviteService;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, staff invites, password/email management, devices")
public class AuthController {

    private final AuthService authService;
    private final InstitutionService institutionService;
    private final UserService userService;
    private final InviteService inviteService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(authService.login(request, userAgent, ipAddress));
    }

    // Move this session into another institution the caller belongs to (or,
    // for a platform admin only, any institution — audited).
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/switch-institution")
    public ResponseEntity<AuthResponse> switchInstitution(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody SwitchInstitutionRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authService.switchInstitution(principal.userPublicId(), request.getInstitutionPublicId(),
                httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody String refreshToken
    ) {
        String accessToken = authHeader.replace("Bearer ", "");
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.userPublicId(), request);
        return ResponseEntity.noContent().build();
    }

    // SUPER_ADMIN, or ADMIN with MANAGE_USERS for non-admin accounts (checked via
    // UserService.requireCanEdit). Previously open to any authenticated user, which
    // let anyone change another account's email and then reset its password.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PatchMapping("/{targetUserPublicId}/email")
    public ResponseEntity<Void> adminChangeEmail(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID targetUserPublicId,
            @Valid @RequestBody AdminChangeEmailRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        userService.requireCanEdit(caller, userService.findInCallerInstitution(targetUserPublicId, caller));
        authService.adminChangeEmail(targetUserPublicId, request, principal.userPublicId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/staff-invites")
    public ResponseEntity<Void> createStaffInvite(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody CreateStaffInviteRequest request
    ) {
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        inviteService.create(request, institution, caller);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // Brand-new invitee: sets a password, which creates their account and
    // membership, and signs them in. An invitee who already has an account
    // gets 409 and accepts via POST /api/invites/accept after signing in.
    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptStaffInvite(
            @Valid @RequestBody AcceptStaffInviteRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(inviteService.acceptAsNewAccount(request,
                httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }

    @GetMapping("/devices")
    public ResponseEntity<List<ActiveDeviceResponse>> getActiveDevices(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        return ResponseEntity.ok(authService.getActiveDevices(principal.userPublicId()));
    }

    @DeleteMapping("/devices/{publicId}")
    public ResponseEntity<Void> revokeDevice(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        authService.revokeDevice(principal.userPublicId(), publicId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request.getRefreshToken()));
    }

    // ── Email OTP (unauthenticated — works mid-registration) ──────────────

    @PostMapping("/send-email-code")
    public ResponseEntity<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        authService.sendEmailVerificationCode(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<Void> verifyEmailCode(@Valid @RequestBody VerifyEmailCodeRequest request) {
        authService.verifyEmailCode(request.getEmail(), request.getCode());
        return ResponseEntity.noContent().build();
    }

    // ── Phone OTP (authenticated — user verifies their own number) ────────

    @PostMapping("/send-phone-otp")
    public ResponseEntity<Void> sendPhoneOtp(@AuthenticationPrincipal CadencePrincipal principal) {
        authService.sendPhoneOtp(principal.userPublicId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-phone-otp")
    public ResponseEntity<Void> verifyPhoneOtp(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody VerifyPhoneOtpRequest request
    ) {
        authService.verifyPhoneOtp(principal.userPublicId(), request.getCode());
        return ResponseEntity.noContent().build();
    }
}