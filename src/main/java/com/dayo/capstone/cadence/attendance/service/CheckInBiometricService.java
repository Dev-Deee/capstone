package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.response.BiometricChallenge;
import com.dayo.capstone.cadence.security.dto.request.WebAuthnAuthenticationRequest;
import com.dayo.capstone.cadence.security.model.WebAuthnCredential;
import com.dayo.capstone.cadence.security.service.WebAuthnService;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * PRD Section 6.12 — the biometric step of check-in, shared by class sessions
 * and campus resumption. Order: QR (routing) → code (the credential) → this
 * step (required when the student is webAuthnCapable) → done.
 *
 * Reuses the login WebAuthn mechanism (WebAuthnService) — same credentials,
 * same challenge store, same signature and signCount checks. The fingerprint /
 * Face ID match happens on the device; the server only ever sees a signed
 * assertion, and the record keeps only the boolean outcome.
 *
 * This is a gate, not a score: it decides whether a check-in completes, and
 * contributes nothing to any confidence score.
 */
@Service
@RequiredArgsConstructor
public class CheckInBiometricService {

    /**
     * How long after a session closes a check-in whose code was accepted while
     * it was open may still be confirmed. The record already exists (it was
     * created before closure, which the hard boundary allows); this only covers
     * the seconds a fingerprint prompt or a retried request can take.
     */
    public static final Duration CONFIRM_GRACE = Duration.ofMinutes(2);

    private final WebAuthnService webAuthnService;

    public boolean isRequiredFor(User student) {
        return student.isWebAuthnCapable();
    }

    public BiometricChallenge challenge(String key, User student) {
        return BiometricChallenge.builder()
                .challenge(webAuthnService.issueChallenge(key))
                .rpId(webAuthnService.rpId())
                .credentialIds(webAuthnService.credentialIdsFor(student))
                .build();
    }

    /**
     * Verifies the student's assertion, requiring on-device user verification
     * (a tap alone is not enough). Any failure — including a credential that
     * belongs to someone else — is a 400, never treated as verified.
     */
    public void verify(String key, User student, WebAuthnAuthenticationRequest request) {
        WebAuthnCredential credential;
        try {
            credential = webAuthnService.verifyAssertion(key, request, true);
        } catch (BadCredentialsException e) {
            throw new IllegalArgumentException("Biometric confirmation failed — try again, or ask your facilitator to mark you");
        }
        if (!credential.getUser().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Biometric confirmation failed — try again, or ask your facilitator to mark you");
        }
    }

    /** Rejects confirmation once the session has been closed for longer than the grace period. */
    public void requireWithinGrace(boolean currentlyOpen, LocalDateTime closedAt, LocalDateTime expiresAt) {
        if (currentlyOpen) return;
        LocalDateTime ended = closedAt != null && closedAt.isBefore(expiresAt) ? closedAt : expiresAt;
        if (ended.plus(CONFIRM_GRACE).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This session has closed — ask your facilitator to mark you");
        }
    }
}
