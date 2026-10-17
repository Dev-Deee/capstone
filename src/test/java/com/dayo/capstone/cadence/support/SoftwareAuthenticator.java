package com.dayo.capstone.cadence.support;

import com.dayo.capstone.cadence.security.model.WebAuthnCredential;
import com.dayo.capstone.cadence.user.model.User;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.util.Base64UrlUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

/**
 * A platform authenticator in software: a P-256 key whose public half is stored
 * exactly as WebAuthnService.completeRegistration() stores a real one, and which
 * signs genuine WebAuthn assertions — so tests exercise the real verification,
 * not a stub. {@code userVerified} models whether the device checked the
 * fingerprint / face (the UV flag).
 */
public class SoftwareAuthenticator {

    private final KeyPair keyPair;
    private final byte[] credentialId;
    private int signCount = 0;

    public SoftwareAuthenticator() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            keyPair = gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        credentialId = new byte[16];
        new SecureRandom().nextBytes(credentialId);
    }

    public String credentialId() {
        return Base64UrlUtil.encodeToString(credentialId);
    }

    /** The row registration would have created for {@code owner}. */
    public WebAuthnCredential credentialFor(User owner) {
        AttestedCredentialData acd = new AttestedCredentialData(AAGUID.ZERO, credentialId,
                EC2COSEKey.create((ECPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.ES256));
        WebAuthnCredential c = new WebAuthnCredential();
        c.setUser(owner);
        c.setCredentialId(credentialId());
        c.setPublicKeyCose(Base64.getEncoder().encodeToString(
                new AttestedCredentialDataConverter(new ObjectConverter()).convert(acd)));
        c.setDeviceLabel("software authenticator");
        return c;
    }

    /** An assertion over {@code challenge}, as navigator.credentials.get() would return it. */
    public Map<String, String> assertion(String challenge, String rpId, String origin, boolean userVerified) {
        try {
            byte[] clientData = ("{\"type\":\"webauthn.get\",\"challenge\":\"" + challenge
                    + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}").getBytes(StandardCharsets.UTF_8);
            byte flags = (byte) (0x01 | (userVerified ? 0x04 : 0)); // UP, optionally UV
            byte[] authData = ByteBuffer.allocate(37)
                    .put(sha256(rpId.getBytes(StandardCharsets.UTF_8)))
                    .put(flags)
                    .putInt(++signCount)
                    .array();
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(authData);
            signer.update(sha256(clientData));
            return Map.of(
                    "credentialId", credentialId(),
                    "authenticatorData", Base64UrlUtil.encodeToString(authData),
                    "clientDataJSON", Base64UrlUtil.encodeToString(clientData),
                    "signature", Base64UrlUtil.encodeToString(signer.sign()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] in) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(in);
    }
}
