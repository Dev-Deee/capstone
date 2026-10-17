package com.dayo.capstone.cadence.security.controller;

import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.security.dto.request.AcceptInviteAsMemberRequest;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.dto.response.InvitePreviewResponse;
import com.dayo.capstone.cadence.security.service.InviteService;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
@Tag(name = "Invites", description = "Joining an institution by invitation")
public class InviteController {

    private final InviteService inviteService;
    private final UserService userService;

    // Public: what the accept page needs to show (institution, role, and
    // whether to set a password or sign in). The token itself is the secret.
    @GetMapping("/{token}")
    public ResponseEntity<InvitePreviewResponse> preview(@PathVariable String token) {
        return ResponseEntity.ok(inviteService.preview(token));
    }

    // Existing account: accept while signed in as the invited email address.
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/accept")
    public ResponseEntity<AuthResponse> accept(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody AcceptInviteAsMemberRequest request,
            HttpServletRequest httpRequest
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(inviteService.acceptAsExistingAccount(request.getToken(), caller,
                httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }
}
