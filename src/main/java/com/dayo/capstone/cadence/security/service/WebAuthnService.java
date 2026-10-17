package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.security.dto.request.WebAuthnAuthenticationRequest;
import com.dayo.capstone.cadence.security.dto.request.WebAuthnRegistrationRequest;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.dto.response.WebAuthnRegistrationOptionsResponse;
import com.dayo.capstone.cadence.security.model.WebAuthnCredential;
import com.dayo.capstone.cadence.security.repository.RefreshTokenRepository;
import com.dayo.capstone.cadence.security.repository.WebAuthnCredentialRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import com.webauthn4j.verifier.exception.VerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PRD Section 6.12 — on-device biometric confirms device OWNERSHIP, not
 * physical presence on its own. It layers onto, never replaces, the
 * existing code-verification flow (Section 6.1). Never mandatory — only
 * required where webAuthnCapable is true (Section 18.2's exception rule),
 * with graceful Code-only fallback everywhere else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebAuthnService {

    private final WebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SessionTokenService sessionTokenService;

    @Value("${app.webauthn.rp-id}")
    private String rpId;

    @Value("${app.webauthn.rp-name}")
    private String rpName;

    @Value("${app.webauthn.origin}")
    private String origin;

    // In-memory challenge store — acceptable for a single-instance pilot;
    // a multi-instance deployment would need this in Redis/DB instead,
    // same class of tradeoff already named for the login rate-limiting
    // discussion earlier in this build.
    private final ConcurrentHashMap<String, Challenge> pendingChallenges = new ConcurrentHashMap<>();

    private final ObjectConverter objectConverter = new ObjectConverter();
    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final AttestedCredentialDataConverter acdConverter = new AttestedCredentialDataConverter(objectConverter);

    public WebAuthnRegistrationOptionsResponse startRegistration(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Challenge challenge = new DefaultChallenge();
        pendingChallenges.put(userPublicId.toString(), challenge);

        return WebAuthnRegistrationOptionsResponse.builder()
                .challenge(Base64UrlUtil.encodeToString(challenge.getValue()))
                .userId(userPublicId.toString())
                .userName(user.getEmail())
                .rpId(rpId)
                .rpName(rpName)
                .build();
    }

    @Transactional
    public void completeRegistration(UUID userPublicId, WebAuthnRegistrationRequest request) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Challenge challenge = pendingChallenges.remove(userPublicId.toString());
        if (challenge == null) {
            throw new IllegalArgumentException("No pending registration challenge — request new options first");
        }

        ServerProperty serverProperty = new ServerProperty(
                new Origin(origin), rpId, challenge, null);

        RegistrationRequest webAuthnRequest = new RegistrationRequest(
                Base64UrlUtil.decode(request.getAttestationObject()),
                Base64UrlUtil.decode(request.getClientDataJSON())
        );
        RegistrationParameters parameters = new RegistrationParameters(serverProperty, null, false, true);

        RegistrationData registrationData = webAuthnManager.parse(webAuthnRequest);
        webAuthnManager.validate(registrationData, parameters);

        // Store the AttestedCredentialData bytes using webauthn4j's own binary format
        // (AAGUID + credentialId + CBOR COSEKey). AttestedCredentialDataConverter
        // provides a lossless round-trip — this is the only safe way to reconstruct
        // the COSEKey later, since a COSEKey cannot be recreated from JCE DER bytes.
        AttestedCredentialData acd = registrationData.getAttestationObject()
                .getAuthenticatorData().getAttestedCredentialData();

        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setUser(user);
        credential.setCredentialId(request.getCredentialId());
        credential.setPublicKeyCose(Base64.getEncoder().encodeToString(acdConverter.convert(acd)));
        credential.setSignCount(registrationData.getAttestationObject().getAuthenticatorData().getSignCount());
        credential.setDeviceLabel(request.getDeviceLabel());
        credentialRepository.save(credential);

        // First successful registration flips capability — detected, not
        // self-reported, per Section 6.12's explicit rule.
        user.setWebAuthnCapable(true);
        userRepository.save(user);

        log.info("WebAuthn credential registered: userPublicId={}", userPublicId);
    }

    public WebAuthnRegistrationOptionsResponse startAuthentication(String credentialIdHint) {
        Challenge challenge = new DefaultChallenge();
        pendingChallenges.put(credentialIdHint, challenge);

        return WebAuthnRegistrationOptionsResponse.builder()
                .challenge(Base64UrlUtil.encodeToString(challenge.getValue()))
                .rpId(rpId)
                .rpName(rpName)
                .build();
    }

    @Transactional
    public AuthResponse completeAuthentication(WebAuthnAuthenticationRequest request, String userAgent, String ipAddress) {
        // userVerificationRequired = false, userPresenceRequired = true — matching
        // the RegistrationParameters used in completeRegistration() exactly.
        WebAuthnCredential credential = verifyAssertion(request.getCredentialId(), request, false);

        // JWT issuance is deliberately AFTER verifyAssertion() — a successful
        // return from it without throwing is the only gate to reaching these lines.
        // Same session issuance as password login (also persists the refresh
        // token, which this path previously returned without storing).
        User user = credential.getUser();
        log.info("WebAuthn authentication successful: userPublicId={}", user.getPublicId());
        return sessionTokenService.issue(user, sessionTokenService.defaultInstitutionFor(user), userAgent, ipAddress);
    }

    // ── Shared with attendance check-in (PRD Section 6.12) ──────────────────

    /** Issues a fresh assertion challenge under {@code key}, replacing any pending one. */
    public String issueChallenge(String key) {
        Challenge challenge = new DefaultChallenge();
        pendingChallenges.put(key, challenge);
        return Base64UrlUtil.encodeToString(challenge.getValue());
    }

    public String rpId() {
        return rpId;
    }

    /** The credential IDs a user has registered — sent as allowCredentials so the browser only offers this student's keys. */
    @Transactional(readOnly = true)
    public List<String> credentialIdsFor(User user) {
        return credentialRepository.findByUser(user).stream().map(WebAuthnCredential::getCredentialId).toList();
    }

    /**
     * Verifies an assertion against the challenge pending under {@code challengeKey}
     * and returns the credential that signed it. The challenge is single-use: it is
     * consumed whether or not verification succeeds.
     *
     * @param userVerificationRequired true when the authenticator must have verified
     *        the user locally (fingerprint / Face ID / device PIN), not just seen a tap.
     * @throws BadCredentialsException on any failure — unknown credential, missing
     *        challenge, bad signature, wrong challenge/origin, replayed signCount.
     */
    @Transactional
    public WebAuthnCredential verifyAssertion(
            String challengeKey, WebAuthnAuthenticationRequest request, boolean userVerificationRequired) {
        Challenge challenge = pendingChallenges.remove(challengeKey);
        WebAuthnCredential credential = request.getCredentialId() == null ? null
                : credentialRepository.findByCredentialId(request.getCredentialId()).orElse(null);
        if (credential == null) {
            throw new BadCredentialsException("Unknown credential");
        }
        if (challenge == null) {
            throw new BadCredentialsException("No pending challenge — request a new one first");
        }

        ServerProperty serverProperty = new ServerProperty(new Origin(origin), rpId, challenge, null);

        // Reconstruct AttestedCredentialData from the stored bytes. This is the
        // exact inverse of how completeRegistration() serialized it: the
        // AttestedCredentialDataConverter round-trip preserves the COSE key
        // structure that webauthn4j needs for signature verification.
        AttestedCredentialData acd = acdConverter.convert(
                Base64.getDecoder().decode(credential.getPublicKeyCose()));
        AuthenticatorImpl authenticator = new AuthenticatorImpl(acd, null, credential.getSignCount());
        AuthenticationParameters authParams = new AuthenticationParameters(
                serverProperty, authenticator, userVerificationRequired, true);

        // parse() deserializes the wire bytes; validate() performs the actual
        // cryptographic signature check AND signCount replay-attack check.
        // validate() throws a subclass of VerificationException on any failure
        // (bad signature, wrong challenge, cloned authenticator, etc.).
        AuthenticationData authData;
        try {
            AuthenticationRequest webAuthnRequest = new AuthenticationRequest(
                    Base64UrlUtil.decode(request.getCredentialId()),
                    Base64UrlUtil.decode(request.getAuthenticatorData()),
                    Base64UrlUtil.decode(request.getClientDataJSON()),
                    Base64UrlUtil.decode(request.getSignature())
            );
            authData = webAuthnManager.parse(webAuthnRequest);
            webAuthnManager.validate(authData, authParams);
        } catch (VerificationException | DataConversionException | IllegalArgumentException e) {
            log.warn("WebAuthn assertion failed: credentialId={}, reason={}",
                    request.getCredentialId(), e.getMessage());
            throw new BadCredentialsException("Biometric verification failed");
        }

        // signCount MUST be updated after every successful assertion — the
        // authenticator increments it on each use, and webauthn4j will reject any
        // future assertion whose signCount is not strictly greater than this stored
        // value (anti-cloning protection, WebAuthn spec §6.1 step 17).
        credential.setSignCount(authData.getAuthenticatorData().getSignCount());
        return credentialRepository.save(credential);
    }
}