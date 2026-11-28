package com.dayo.capstone.cadence.notification;

import com.dayo.capstone.cadence.notification.service.WebPushCrypto;
import com.dayo.capstone.cadence.notification.service.WebPushCryptoTestAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Web Push cryptography against the published test vector, and the VAPID token's shape. */
class WebPushCryptoTest {

    private static final Base64.Decoder D = Base64.getUrlDecoder();
    private static final Base64.Encoder E = Base64.getUrlEncoder().withoutPadding();

    @Test
    void encryptionMatchesRfc8291AppendixA() throws Exception {
        byte[] asPublic = D.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] asPrivate = D.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] uaPublic = D.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] salt = D.decode("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] auth = D.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] plaintext = D.decode("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24");

        ECPublicKey pub = WebPushCryptoTestAccess.publicKey(asPublic);
        PrivateKey priv = KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(new BigInteger(1, asPrivate), pub.getParams()));

        byte[] message = WebPushCryptoTestAccess.encrypt(plaintext, uaPublic, auth, new KeyPair(pub, priv), salt);

        assertThat(E.encodeToString(message)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void vapidTokenIsAnEs256JwtForTheEndpointOrigin() throws Exception {
        String[] keys = vapidKeys();
        WebPushCrypto.Vapid vapid = WebPushCrypto.parseVapid(keys[0], keys[1], "mailto:ops@cadence.test");
        Instant now = Instant.ofEpochSecond(1_800_000_000L);

        String header = WebPushCrypto.vapidAuthorization(vapid, URI.create("https://fcm.googleapis.com/fcm/send/abc"), now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + keys[0]);
        String jwt = header.substring("vapid t=".length(), header.indexOf(','));
        String[] parts = jwt.split("\\.");
        JsonNode claims = new ObjectMapper().readTree(D.decode(parts[1]));
        assertThat(claims.get("aud").asText()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("sub").asText()).isEqualTo("mailto:ops@cadence.test");
        assertThat(claims.get("exp").asLong()).isEqualTo(now.getEpochSecond() + 12 * 3600);
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(vapid.publicKey());
        s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(s.verify(D.decode(parts[2]))).isTrue();
    }

    @Test
    void mismatchedOrMalformedVapidKeysAreRejected() throws Exception {
        String[] a = vapidKeys();
        String[] b = vapidKeys();
        assertThatThrownBy(() -> WebPushCrypto.parseVapid(a[0], b[1], "mailto:x@y.z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPushCrypto.parseVapid("not-a-key", a[1], "mailto:x@y.z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPushCrypto.parseVapid(a[0], a[1], "ops@cadence.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A fresh {publicKey, privateKey} in the base64url format the app reads from the environment. */
    public static String[] vapidKeys() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = gen.generateKeyPair();
        byte[] d = ((java.security.interfaces.ECPrivateKey) kp.getPrivate()).getS().toByteArray();
        byte[] d32 = new byte[32];
        int len = Math.min(d.length, 32);
        System.arraycopy(d, d.length - len, d32, 32 - len, len);
        return new String[]{E.encodeToString(WebPushCryptoTestAccess.uncompressed((ECPublicKey) kp.getPublic())), E.encodeToString(d32)};
    }
}
