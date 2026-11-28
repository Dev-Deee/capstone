package com.dayo.capstone.cadence.messaging.controller;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.messaging.dto.ConversationResponse;
import com.dayo.capstone.cadence.messaging.dto.MessagePage;
import com.dayo.capstone.cadence.messaging.dto.MessageResponse;
import com.dayo.capstone.cadence.messaging.dto.PersonRef;
import com.dayo.capstone.cadence.messaging.dto.SendMessageRequest;
import com.dayo.capstone.cadence.messaging.dto.StartConversationRequest;
import com.dayo.capstone.cadence.messaging.service.ConversationService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Direct messages in the institution the caller is working in. Polled by the
// web app (no WebSockets). Who may message whom is decided in MessagingPolicy.
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Messages", description = "1:1 direct messages within an institution")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserService userService;
    private final InstitutionService institutionService;

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(@AuthenticationPrincipal CadencePrincipal p) {
        return ResponseEntity.ok(conversationService.list(caller(p), institution(p)));
    }

    // Total unread messages here, for the sidebar badge.
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal CadencePrincipal p) {
        return ResponseEntity.ok(Map.of("count", conversationService.unreadCount(caller(p), institution(p))));
    }

    // People I may start a conversation with, optionally filtered by name.
    @GetMapping("/contacts")
    public ResponseEntity<List<PersonRef>> contacts(@AuthenticationPrincipal CadencePrincipal p,
                                                    @RequestParam(required = false) String q) {
        return ResponseEntity.ok(conversationService.contacts(q, caller(p), institution(p)));
    }

    // Get-or-create the conversation with one person.
    @PostMapping
    public ResponseEntity<ConversationResponse> start(@AuthenticationPrincipal CadencePrincipal p,
                                                      @Valid @RequestBody StartConversationRequest request) {
        return ResponseEntity.ok(conversationService.start(request.getUserPublicId(), caller(p), institution(p)));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ConversationResponse> get(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(conversationService.get(publicId, caller(p), institution(p)));
    }

    // Oldest → newest. ?after=<messageId> for polling, ?before=<messageId> for older history.
    @GetMapping("/{publicId}/messages")
    public ResponseEntity<MessagePage> messages(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                @RequestParam(required = false) UUID before,
                                                @RequestParam(required = false) UUID after,
                                                @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(conversationService.messages(publicId, before, after, size, caller(p), institution(p)));
    }

    @PostMapping("/{publicId}/messages")
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.send(publicId, request.getBody(), caller(p), institution(p)));
    }

    @PostMapping("/{publicId}/read")
    public ResponseEntity<ConversationResponse> markRead(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(conversationService.markRead(publicId, caller(p), institution(p)));
    }

    private User caller(CadencePrincipal p) {
        return userService.findEntityByPublicId(p.userPublicId());
    }

    private Institution institution(CadencePrincipal p) {
        return institutionService.findEntityByUserPublicId(p.userPublicId());
    }
}
