package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.request.OpenSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.SessionResponse;
import com.dayo.capstone.cadence.attendance.service.SessionService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "PRD Section 6 — attendance sessions, session-code verification")
public class SessionController {

    private final SessionService sessionService;
    private final UserService userService;
    private final InstitutionService institutionService;

    // Facilitator-only — enforced both by role and by the actual
    // FacilitatorCohort assignment check inside the service.
    @PreAuthorize("hasRole('FACILITATOR')")
    @PostMapping
    public ResponseEntity<SessionResponse> open(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody OpenSessionRequest request
    ) {
        User facilitator = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.open(request, facilitator, institution));
    }

    @PreAuthorize("hasRole('FACILITATOR')")
    @PatchMapping("/{publicId}/close")
    public ResponseEntity<SessionResponse> close(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User facilitator = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(sessionService.close(publicId, facilitator));
    }
}