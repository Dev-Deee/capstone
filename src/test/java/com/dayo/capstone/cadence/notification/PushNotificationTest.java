package com.dayo.capstone.cadence.notification;

import com.dayo.capstone.cadence.common.event.DomainEvent;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.StudentCohortChangedEvent;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.notification.model.PushSubscription;
import com.dayo.capstone.cadence.notification.repository.PushSubscriptionRepository;
import com.dayo.capstone.cadence.notification.service.PushGateway;
import com.dayo.capstone.cadence.notification.service.WebPushCryptoTestAccess;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Web Push with VAPID keys configured (generated per run): subscription CRUD
 * and ownership, and each new in-app notification pushed to the recipient's
 * browsers through a mocked push service.
 */
class PushNotificationTest extends IntegrationTestBase {

    private static final String[] VAPID = vapid();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @DynamicPropertySource
    static void vapidKeys(DynamicPropertyRegistry registry) {
        registry.add("app.push.vapid-public-key", () -> VAPID[0]);
        registry.add("app.push.vapid-private-key", () -> VAPID[1]);
        registry.add("app.push.vapid-subject", () -> "mailto:ops@cadence.test");
    }

    @MockitoBean PushGateway gateway;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired DomainEvents domainEvents;
    @Autowired TransactionTemplate tx;

    Institution inst;
    User student;

    @BeforeEach
    void setUp() throws Exception {
        inst = institution("Push Academy");
        student = user("pushstu");
        member(student, inst, Role.STUDENT);
        when(gateway.send(any(), anyMap(), any())).thenReturn(201);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** A browser: its key pair and auth secret, and the subscription JSON it would send. */
    record Browser(KeyPair keys, byte[] auth, String endpoint) {
        Map<String, Object> json() {
            return Map.of("endpoint", endpoint, "keys", Map.of(
                    "p256dh", B64.encodeToString(WebPushCryptoTestAccess.uncompressed((ECPublicKey) keys.getPublic())),
                    "auth", B64.encodeToString(auth)));
        }
    }

    private static Browser browser() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            return new Browser(gen.generateKeyPair(), auth, "https://push.example.test/send/" + UUID.randomUUID());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String[] vapid() {
        try {
            return WebPushCryptoTest.vapidKeys();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("Timed out waiting");
    }

    private void notifyStudent() {
        tx.executeWithoutResult(s -> domainEvents.publish((DomainEvent)
                new StudentCohortChangedEvent(inst.getId(), null, student.getId(), null, "Cohort Push", UUID.randomUUID())));
    }

    /** The user agent's side of RFC 8291: decrypt a pushed message. */
    private static JsonNode decrypt(Browser b, byte[] message) throws Exception {
        byte[] salt = Arrays.copyOfRange(message, 0, 16);
        int idLen = message[20] & 0xff;
        byte[] asPublic = Arrays.copyOfRange(message, 21, 21 + idLen);
        byte[] ciphertext = Arrays.copyOfRange(message, 21 + idLen, message.length);
        byte[] uaPublic = WebPushCryptoTestAccess.uncompressed((ECPublicKey) b.keys().getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(b.keys().getPrivate());
        ka.doPhase(WebPushCryptoTestAccess.publicKey(asPublic), true);
        byte[] prkKey = hmac(b.auth(), ka.generateSecret());
        byte[] ikm = hmac(prkKey, cat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, cat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, cat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = c.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return new ObjectMapper().readTree(Arrays.copyOf(padded, padded.length - 1));
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void publicKeyIsServedToSignedInUsersOnly() {
        Resp r = get("/api/push/public-key", login(student));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("enabled").asBoolean()).isTrue();
        assertThat(r.text("publicKey")).isEqualTo(VAPID[0]);
        assertThat(get("/api/push/public-key", null).status()).isIn(401, 403);
    }

    @Test
    void subscribeIsIdempotentAndOnlyTheOwnerCanUnsubscribe() {
        String token = login(student);
        Browser b = browser();
        assertThat(call("POST", "/api/push/subscriptions", b.json(), token).status()).isEqualTo(201);
        assertThat(call("POST", "/api/push/subscriptions", b.json(), token).status()).isEqualTo(201);
        PushSubscription saved = subscriptions.findByEndpoint(b.endpoint()).orElseThrow();
        assertThat(subscriptions.findByUserOldestFirst(student.getId())).hasSize(1);
        assertThat(saved.getUserAgent()).isEqualTo("cadence-tests");

        // Someone else can't remove it.
        User other = user("pushother");
        member(other, inst, Role.STUDENT);
        assertThat(call("DELETE", "/api/push/subscriptions", Map.of("endpoint", b.endpoint()), login(other)).status())
                .isEqualTo(204);
        assertThat(subscriptions.findByEndpoint(b.endpoint())).isPresent();

        // The owner can.
        assertThat(call("DELETE", "/api/push/subscriptions", Map.of("endpoint", b.endpoint()), token).status())
                .isEqualTo(204);
        assertThat(subscriptions.findByEndpoint(b.endpoint())).isEmpty();
        assertThat(call("POST", "/api/push/subscriptions", b.json(), null).status()).isIn(401, 403);
    }

    @Test
    void theSameBrowserSignedInAsSomeoneElseMovesToThem() {
        Browser b = browser();
        call("POST", "/api/push/subscriptions", b.json(), login(student));
        User other = user("pushnext");
        member(other, inst, Role.STUDENT);
        assertThat(call("POST", "/api/push/subscriptions", b.json(), login(other)).status()).isEqualTo(201);
        assertThat(subscriptions.findByUserOldestFirst(student.getId())).isEmpty();
        assertThat(subscriptions.findByUserOldestFirst(other.getId())).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endpointsOutsideKnownPushServicesAndBadKeysAreRejected() {
        String token = login(student);
        Browser b = browser();
        Map<String, Object> keys = (Map<String, Object>) b.json().get("keys");
        assertThat(call("POST", "/api/push/subscriptions",
                Map.of("endpoint", "https://169.254.169.254/latest", "keys", keys), token).status()).isEqualTo(400);
        assertThat(call("POST", "/api/push/subscriptions",
                Map.of("endpoint", "http://push.example.test/x", "keys", keys), token).status()).isEqualTo(400);
        assertThat(call("POST", "/api/push/subscriptions",
                Map.of("endpoint", b.endpoint(), "keys", Map.of("p256dh", "AAAA", "auth", keys.get("auth"))), token).status())
                .isEqualTo(400);
        assertThat(call("POST", "/api/push/subscriptions", Map.of("endpoint", b.endpoint()), token).status()).isEqualTo(400);
    }

    @Test
    void aNewNotificationIsPushedEncryptedToTheRecipientsBrowsers() throws Exception {
        Browser b = browser();
        assertThat(call("POST", "/api/push/subscriptions", b.json(), login(student)).status()).isEqualTo(201);

        notifyStudent();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(gateway, timeout(5000)).send(eq(URI.create(b.endpoint())), headers.capture(), body.capture());

        assertThat(headers.getValue()).containsEntry("Content-Encoding", "aes128gcm").containsKey("TTL");
        assertThat(headers.getValue().get("Authorization")).startsWith("vapid t=").endsWith("k=" + VAPID[0]);
        JsonNode payload = decrypt(b, body.getValue());
        assertThat(payload.get("title").asText()).isEqualTo("You've joined Cohort Push");
        assertThat(payload.get("body").asText()).contains("Cohort Push");
        assertThat(payload.get("url").asText()).isEqualTo("http://localhost:3000/dashboard/student");

        await(() -> subscriptions.findByEndpoint(b.endpoint()).map(s -> s.getLastSuccessAt() != null).orElse(false));
    }

    @Test
    void aSubscriptionThePushServiceReportsGoneIsRemoved() throws Exception {
        Browser gone = browser();
        Browser live = browser();
        String token = login(student);
        call("POST", "/api/push/subscriptions", gone.json(), token);
        call("POST", "/api/push/subscriptions", live.json(), token);
        when(gateway.send(eq(URI.create(gone.endpoint())), anyMap(), any())).thenReturn(410);

        notifyStudent();

        await(() -> subscriptions.findByEndpoint(gone.endpoint()).isEmpty());
        verify(gateway, timeout(5000)).send(eq(URI.create(live.endpoint())), anyMap(), any());
        assertThat(subscriptions.findByEndpoint(live.endpoint())).isPresent();
    }
}
