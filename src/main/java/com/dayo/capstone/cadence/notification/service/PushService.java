package com.dayo.capstone.cadence.notification.service;

import com.dayo.capstone.cadence.common.exception.ConflictException;
import com.dayo.capstone.cadence.notification.dto.PushSubscriptionRequest;
import com.dayo.capstone.cadence.notification.model.PushSubscription;
import com.dayo.capstone.cadence.notification.repository.PushSubscriptionRepository;
import com.dayo.capstone.cadence.notification.service.NotificationService.Draft;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Web Push: the browsers a user has subscribed, and sending each new in-app
 * notification to them.
 *
 * - Off unless VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY and VAPID_SUBJECT are all
 *   set (and valid). Off, nothing is sent and subscribing is refused; in-app
 *   and email delivery are unaffected.
 * - Sending is asynchronous on the event executor and best-effort: a failure
 *   is logged and never reaches the notification that triggered it.
 * - A subscription the push service reports gone (404/410) is deleted.
 * - Endpoints must be https on a known push service host (configurable), so
 *   the server can't be pointed at arbitrary URLs.
 */
@Slf4j
@Service
public class PushService {

    /** Devices per user; subscribing another drops the oldest. */
    static final int MAX_SUBSCRIPTIONS_PER_USER = 10;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BODY_CHARS = 1000;

    private final PushSubscriptionRepository repository;
    private final PushGateway gateway;
    private final WebPushCrypto.Vapid vapid; // null = push disabled
    private final String frontendUrl;
    private final List<String> allowedHosts;
    private final int ttlSeconds;

    public PushService(PushSubscriptionRepository repository, PushGateway gateway,
                       @Value("${app.push.vapid-public-key:}") String publicKey,
                       @Value("${app.push.vapid-private-key:}") String privateKey,
                       @Value("${app.push.vapid-subject:}") String subject,
                       @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
                       @Value("${app.push.allowed-hosts:fcm.googleapis.com,android.googleapis.com,push.services.mozilla.com,notify.windows.com,push.apple.com}") String allowedHosts,
                       @Value("${app.push.ttl-seconds:86400}") int ttlSeconds) {
        this.repository = repository;
        this.gateway = gateway;
        this.vapid = loadVapid(publicKey, privateKey, subject);
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).toList();
        this.ttlSeconds = ttlSeconds;
    }

    private static WebPushCrypto.Vapid loadVapid(String publicKey, String privateKey, String subject) {
        if (publicKey.isBlank() && privateKey.isBlank() && subject.isBlank()) {
            log.info("Push notifications disabled: VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY / VAPID_SUBJECT not set");
            return null;
        }
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            log.warn("Push notifications DISABLED: set all of VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY and VAPID_SUBJECT");
            return null;
        }
        try {
            WebPushCrypto.Vapid v = WebPushCrypto.parseVapid(publicKey, privateKey, subject);
            log.info("Push notifications enabled (VAPID subject {})", subject);
            return v;
        } catch (IllegalArgumentException e) {
            log.warn("Push notifications DISABLED: invalid VAPID configuration — {}", e.getMessage());
            return null;
        }
    }

    public boolean enabled() {
        return vapid != null;
    }

    /** The VAPID public key browsers subscribe with, or null when push is off. */
    public String publicKey() {
        return vapid == null ? null : vapid.publicKeyB64();
    }

    // ── Subscriptions ──────────────────────────────────────────────────────

    /** Saves (or refreshes) this browser's subscription for {@code user}. */
    @Transactional
    public PushSubscription subscribe(User user, PushSubscriptionRequest request, String userAgent) {
        if (!enabled()) throw new ConflictException("Push notifications aren't enabled on this server");
        String endpoint = request.endpoint().trim();
        checkEndpoint(endpoint);
        WebPushCrypto.validateSubscriptionKeys(request.keys().p256dh(), request.keys().auth());

        PushSubscription s = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        boolean isNew = s.getId() == null;
        // The same browser, now signed in as someone else, moves to them.
        s.setUser(user);
        s.setEndpoint(endpoint);
        s.setP256dh(request.keys().p256dh());
        s.setAuth(request.keys().auth());
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300)));
        s = repository.save(s);

        if (isNew) {
            List<PushSubscription> mine = repository.findByUserOldestFirst(user.getId());
            for (int i = 0; i < mine.size() - MAX_SUBSCRIPTIONS_PER_USER; i++) repository.delete(mine.get(i));
        }
        return s;
    }

    /** Removes the caller's subscription for this endpoint. Someone else's is left alone. */
    @Transactional
    public boolean unsubscribe(Long userId, String endpoint) {
        return repository.deleteOwned(endpoint.trim(), userId) > 0;
    }

    private void checkEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push endpoint");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                || allowedHosts.stream().noneMatch(h -> host.equals(h) || host.endsWith("." + h))) {
            throw new IllegalArgumentException("Unsupported push endpoint");
        }
    }

    // ── Sending ────────────────────────────────────────────────────────────

    /** Pushes each written notification to its recipient's browsers. Fire-and-forget. */
    @Async("eventExecutor")
    public void push(List<Draft> drafts) {
        if (!enabled() || drafts.isEmpty()) return;
        try {
            Map<Long, List<PushSubscription>> byUser = repository
                    .findForUsers(drafts.stream().map(Draft::recipientId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.groupingBy(s -> s.getUser().getId()));
            for (Draft d : drafts) {
                List<PushSubscription> subs = byUser.getOrDefault(d.recipientId(), List.of());
                if (subs.isEmpty()) continue;
                byte[] payload = payload(d);
                for (PushSubscription s : subs) send(s, payload);
            }
        } catch (Exception e) {
            log.error("Push delivery failed", e);
        }
    }

    private void send(PushSubscription s, byte[] payload) {
        URI endpoint = URI.create(s.getEndpoint());
        try {
            byte[] body = WebPushCrypto.encrypt(payload, s.getP256dh(), s.getAuth());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", WebPushCrypto.vapidAuthorization(vapid, endpoint, Instant.now()));
            headers.put("Content-Encoding", "aes128gcm");
            headers.put("Content-Type", "application/octet-stream");
            headers.put("TTL", String.valueOf(ttlSeconds));
            headers.put("Urgency", "normal");
            int status = gateway.send(endpoint, headers, body);
            if (status >= 200 && status < 300) {
                repository.markSuccess(s.getId(), LocalDateTime.now());
            } else if (status == 404 || status == 410) {
                // Unsubscribed or expired in the browser: it will never accept another message.
                repository.deleteById(s.getId());
                log.info("Push subscription gone ({}), removed: host={}", status, endpoint.getHost());
            } else {
                log.warn("Push rejected: status={}, host={}", status, endpoint.getHost());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // The endpoint's path identifies the device — only the host is logged.
            log.warn("Push send failed: host={}, error={}", endpoint.getHost(), e.toString());
        }
    }

    /** What the service worker shows: {title, body, url}. */
    byte[] payload(Draft d) {
        String route = d.linkRoute() == null || d.linkRoute().isBlank() ? "/dashboard" : d.linkRoute();
        String body = d.body() == null ? "" : d.body();
        if (body.length() > MAX_BODY_CHARS) body = body.substring(0, MAX_BODY_CHARS - 1) + "…";
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", d.title());
        m.put("body", body);
        m.put("url", frontendUrl + (route.startsWith("/") ? route : "/" + route));
        m.put("type", d.type().name());
        try {
            return JSON.writeValueAsString(m).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise push payload", e);
        }
    }
}
