package com.dayo.capstone.cadence.security.controller;

import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.security.dto.request.WebAuthnAuthenticationRequest;
import com.dayo.capstone.cadence.security.dto.request.WebAuthnRegistrationRequest;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.dto.response.WebAuthnRegistrationOptionsResponse;
import com.dayo.capstone.cadence.security.service.WebAuthnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webauthn")
@RequiredArgsConstructor
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    @PostMapping("/register/options")
    public ResponseEntity<WebAuthnRegistrationOptionsResponse> registerOptions(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        return ResponseEntity.ok(webAuthnService.startRegistration(principal.userPublicId()));
    }

    @PostMapping("/register/complete")
    public ResponseEntity<Void> registerComplete(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestBody WebAuthnRegistrationRequest request
    ) {
        webAuthnService.completeRegistration(principal.userPublicId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/authenticate/options")
    public ResponseEntity<WebAuthnRegistrationOptionsResponse> authOptions(
            @RequestParam String credentialIdHint
    ) {
        return ResponseEntity.ok(webAuthnService.startAuthentication(credentialIdHint));
    }

    @PostMapping("/authenticate/complete")
    public ResponseEntity<AuthResponse> authComplete(
            @RequestBody WebAuthnAuthenticationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(webAuthnService.completeAuthentication(
                request, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }
}