package com.dayo.capstone.cadence.notification.controller;

import com.dayo.capstone.cadence.notification.dto.PushEndpointRequest;
import com.dayo.capstone.cadence.notification.dto.PushPublicKeyResponse;
import com.dayo.capstone.cadence.notification.dto.PushSubscriptionRequest;
import com.dayo.capstone.cadence.notification.dto.PushSubscriptionResponse;
import com.dayo.capstone.cadence.notification.model.PushSubscription;
import com.dayo.capstone.cadence.notification.service.PushService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Web Push subscriptions for the signed-in user's browsers. A subscription
 * belongs to the user (not an institution); a user can only remove their own.
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
@Tag(name = "Push notifications", description = "Web Push (VAPID) subscriptions for the current user")
public class PushController {

    private final PushService pushService;
    private final UserService userService;

    /** Whether push is on, and the VAPID public key to subscribe with. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public-key")
    public ResponseEntity<PushPublicKeyResponse> publicKey() {
        return ResponseEntity.ok(new PushPublicKeyResponse(pushService.enabled(), pushService.publicKey()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscriptions")
    public ResponseEntity<PushSubscriptionResponse> subscribe(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        PushSubscription s = pushService.subscribe(
                userService.findEntityByPublicId(principal.userPublicId()), request, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PushSubscriptionResponse(s.getPublicId(), s.getCreatedAt()));
    }

    /** Removes this browser's subscription. Idempotent: an unknown (or someone else's) endpoint is a no-op. */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody PushEndpointRequest request
    ) {
        pushService.unsubscribe(userService.findEntityByPublicId(principal.userPublicId()).getId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
