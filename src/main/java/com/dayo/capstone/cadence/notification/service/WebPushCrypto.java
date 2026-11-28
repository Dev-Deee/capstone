package com.dayo.capstone.cadence.notification.service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * The two pieces of Web Push cryptography, on the JDK alone (no BouncyCastle):
 * - RFC 8291 message encryption with the RFC 8188 {@code aes128gcm} content
 *   coding (ECDH P-256 + HKDF-SHA256 + AES-128-GCM, one record);
 * - RFC 8292 VAPID: an ES256-signed JWT identifying this server.
 *
 * Keys use the format every Web Push tool emits: the public key is the
 * 65-byte uncompressed P-256 point, the private key the 32-byte scalar, both
 * base64url without padding.
 */
public final class WebPushCrypto {

    private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();
    private static final int RECORD_SIZE = 4096;
    private static final long VAPID_TTL_SECONDS = 12 * 60 * 60;

    /** This server's VAPID identity. {@code subject} is a mailto: or https: contact for push services. */
    public record Vapid(ECPublicKey publicKey, ECPrivateKey privateKey, String publicKeyB64, String subject) {}

    private WebPushCrypto() {}

    // ── VAPID ──────────────────────────────────────────────────────────────

    /** Parses and checks a VAPID key pair; throws IllegalArgumentException if it's malformed or mismatched. */
    public static Vapid parseVapid(String publicKeyB64, String privateKeyB64, String subject) {
        if (subject == null || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
            throw new IllegalArgumentException("VAPID subject must start with mailto: or https://");
        }
        ECPublicKey pub = publicKey(decode(publicKeyB64.trim()));
        byte[] d = decode(privateKeyB64.trim());
        if (d.length != 32) throw new IllegalArgumentException("VAPID private key must be 32 bytes");
        ECPrivateKey priv;
        try {
            priv = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
            // The two halves must belong together, or every push service rejects us.
            byte[] probe = "cadence-vapid-check".getBytes(StandardCharsets.UTF_8);
            if (!verify(pub, probe, sign(priv, probe))) {
                throw new IllegalArgumentException("VAPID public and private keys don't match");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid VAPID private key", e);
        }
        return new Vapid(pub, priv, publicKeyB64.trim(), subject);
    }

    /** The {@code Authorization} header value for a request to {@code endpoint}. */
    public static String vapidAuthorization(Vapid vapid, URI endpoint, Instant now) {
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + jsonEscape(audience) + "\",\"exp\":"
                + (now.getEpochSecond() + VAPID_TTL_SECONDS) + ",\"sub\":\"" + jsonEscape(vapid.subject()) + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        try {
            String jwt = signingInput + "." + B64.encodeToString(
                    sign(vapid.privateKey(), signingInput.getBytes(StandardCharsets.US_ASCII)));
            return "vapid t=" + jwt + ", k=" + vapid.publicKeyB64();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign VAPID token", e);
        }
    }

    // ── Message encryption (RFC 8291) ──────────────────────────────────────

    /** Encrypts {@code payload} for a browser with these subscription keys (base64url). */
    public static byte[] encrypt(byte[] payload, String p256dh, String auth) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(payload, decode(p256dh), decode(auth), gen.generateKeyPair(), salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate an ephemeral key", e);
        }
    }

    /** As above with a given ephemeral key pair and salt — fixed only by tests (RFC 8291 Appendix A). */
    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        if (payload.length > RECORD_SIZE - 16 - 1 - 86) throw new IllegalArgumentException("Push payload too large");
        try {
            byte[] asPublic = uncompressed((ECPublicKey) ephemeral.getPublic());
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ephemeral.getPrivate());
            ka.doPhase(publicKey(uaPublic), true);
            byte[] ecdhSecret = ka.generateSecret();

            byte[] prkKey = hmac(authSecret, ecdhSecret);
            byte[] ikm = hmac(prkKey, "WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1});
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // One record, so the padding delimiter is 0x02 ("last record").
            byte[] ciphertext = cipher.doFinal(concat(payload, new byte[]{2}));

            return concat(salt, ByteBuffer.allocate(4).putInt(RECORD_SIZE).array(),
                    new byte[]{(byte) asPublic.length}, asPublic, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push encryption failed", e);
        }
    }

    /** Checks a browser's subscription keys; throws IllegalArgumentException if they can't be used. */
    public static void validateSubscriptionKeys(String p256dh, String auth) {
        publicKey(decode(p256dh));
        if (decode(auth).length != 16) throw new IllegalArgumentException("Invalid subscription auth secret");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    static byte[] decode(String b64url) {
        try {
            return B64_DECODE.decode(b64url.replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64url key");
        }
    }

    static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Invalid P-256 public key");
        }
        try {
            ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256));
        } catch (GeneralSecurityException e) {
            // Includes points that aren't on the curve.
            throw new IllegalArgumentException("Invalid P-256 public key");
        }
    }

    static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[]{4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    private static byte[] fixed32(BigInteger n) {
        byte[] raw = n.toByteArray();
        byte[] out = new byte[32];
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, out, 32 - len, len);
        return out;
    }

    private static byte[] sign(PrivateKey key, byte[] data) throws GeneralSecurityException {
        // P1363 = raw r||s, the JOSE ES256 signature format (not DER).
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    private static boolean verify(PublicKey key, byte[] data, byte[] signature) throws GeneralSecurityException {
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(key);
        s.update(data);
        return s.verify(signature);
    }

    private static byte[] hmac(byte[] key, byte[]... parts) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] p : parts) mac.update(p);
        return mac.doFinal();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }
}
