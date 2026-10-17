package com.dayo.capstone.cadence.notification.controller;

import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.notification.dto.NotificationPage;
import com.dayo.capstone.cadence.notification.dto.NotificationResponse;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.service.NotificationService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The signed-in user's notifications in the institution they're working in.
 * Polled by the web app (no WebSockets — PRD 4.3). Someone else's
 * notification, or one from another institution, is "not found".
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user and institution")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<NotificationPage> list(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long institutionId = TenantContext.institutionId();
        if (institutionId == null) return ResponseEntity.ok(new NotificationPage(List.of(), 0, size, 0, false));
        Page<Notification> result = notificationService.inbox(me(principal).getId(), institutionId, page, size);
        return ResponseEntity.ok(new NotificationPage(
                result.getContent().stream().map(NotificationResponse::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal CadencePrincipal principal) {
        Long institutionId = TenantContext.institutionId();
        long count = institutionId == null ? 0 : notificationService.unreadCount(me(principal).getId(), institutionId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{publicId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        Long institutionId = TenantContext.institutionId();
        if (institutionId == null) {
            throw new com.dayo.capstone.cadence.common.exception.ResourceNotFoundException("Notification not found");
        }
        return ResponseEntity.ok(NotificationResponse.of(
                notificationService.markRead(publicId, me(principal).getId(), institutionId)));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal CadencePrincipal principal) {
        Long institutionId = TenantContext.institutionId();
        int updated = institutionId == null ? 0 : notificationService.markAllRead(me(principal).getId(), institutionId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private User me(CadencePrincipal principal) {
        return userService.findEntityByPublicId(principal.userPublicId());
    }
}
