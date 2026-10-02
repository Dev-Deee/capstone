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
        WebAuthnCredential credential = credentialRepository.findByCredentialId(request.getCredentialId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown credential"));

        Challenge challenge = pendingChallenges.remove(request.getCredentialId());
        if (challenge == null) {
            throw new IllegalArgumentException("No pending authentication challenge — request new options first");
        }

        ServerProperty serverProperty = new ServerProperty(new Origin(origin), rpId, challenge, null);

        AuthenticationRequest webAuthnRequest = new AuthenticationRequest(
                Base64UrlUtil.decode(request.getCredentialId()),
                Base64UrlUtil.decode(request.getAuthenticatorData()),
                Base64UrlUtil.decode(request.getClientDataJSON()),
                Base64UrlUtil.decode(request.getSignature())
        );

        // Reconstruct AttestedCredentialData from the stored bytes. This is the
        // exact inverse of how completeRegistration() serialized it: the
        // AttestedCredentialDataConverter round-trip preserves the COSE key
        // structure that webauthn4j needs for signature verification.
        AttestedCredentialData acd = acdConverter.convert(
                Base64.getDecoder().decode(credential.getPublicKeyCose()));

        // userVerificationRequired = false, userPresenceRequired = true — matching
        // the RegistrationParameters used in completeRegistration() exactly.
        AuthenticatorImpl authenticator = new AuthenticatorImpl(acd, null, credential.getSignCount());
        AuthenticationParameters authParams = new AuthenticationParameters(
                serverProperty, authenticator, false, true);

        // parse() deserializes the wire bytes; validate() performs the actual
        // cryptographic signature check AND signCount replay-attack check. Both
        // must succeed before any JWT is issued. validate() throws a subclass of
        // VerificationException on any failure (bad signature, wrong challenge,
        // cloned authenticator, etc.).
        AuthenticationData authData;
        try {
            authData = webAuthnManager.parse(webAuthnRequest);
            webAuthnManager.validate(authData, authParams);
        } catch (VerificationException | DataConversionException e) {
            log.warn("WebAuthn authentication failed: credentialId={}, reason={}",
                    request.getCredentialId(), e.getMessage());
            throw new BadCredentialsException("Biometric verification failed");
        }

        // signCount MUST be updated after every successful authentication — the
        // authenticator increments it on each use, and webauthn4j will reject any
        // future assertion whose signCount is not strictly greater than this stored
        // value (anti-cloning protection, WebAuthn spec §6.1 step 17).
        credential.setSignCount(authData.getAuthenticatorData().getSignCount());
        credentialRepository.save(credential);

        // JWT issuance is deliberately AFTER validate() — a successful return from
        // validate() without throwing is the only gate to reaching these lines.
        // Same session issuance as password login (also persists the refresh
        // token, which this path previously returned without storing).
        User user = credential.getUser();
        log.info("WebAuthn authentication successful: userPublicId={}", user.getPublicId());
        return sessionTokenService.issue(user, sessionTokenService.defaultInstitutionFor(user), userAgent, ipAddress);
    }
}