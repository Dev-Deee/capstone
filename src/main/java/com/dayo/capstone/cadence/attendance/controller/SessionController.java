package com.dayo.capstone.cadence.attendance.controller;

import com.dayo.capstone.cadence.attendance.dto.request.OpenSessionRequest;
import com.dayo.capstone.cadence.attendance.dto.response.CurrentCodeResponse;
import com.dayo.capstone.cadence.attendance.dto.response.SessionAttendanceRosterEntry;
import com.dayo.capstone.cadence.attendance.dto.response.SessionQrRouteResponse;
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

import java.util.List;
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

    // Facilitator-only — returns the live rotating TOTP code and seconds until rotation.
    // The caller must be the facilitator who opened the session (enforced in the service).
    // Do NOT cache responses; each request computes fresh.
    @PreAuthorize("hasRole('FACILITATOR')")
    @GetMapping("/{publicId}/current-code")
    public ResponseEntity<CurrentCodeResponse> getCurrentCode(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User facilitator = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(sessionService.getCurrentCode(publicId, facilitator));
    }

    // Lists sessions for a cohort+subject. Facilitators see their own sessions only;
    // SUPER_ADMIN sees all for the cohort+subject (scoping enforced in the service).
    @PreAuthorize("hasRole('FACILITATOR') or hasRole('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<SessionResponse>> list(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestParam UUID cohortPublicId,
            @RequestParam UUID subjectPublicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        Institution institution = institutionService.findEntityByUserPublicId(principal.userPublicId());
        return ResponseEntity.ok(sessionService.getSessionsByCohortAndSubject(
                cohortPublicId, subjectPublicId, institution, caller));
    }

    // Attendance roster for a session — present/absent per enrolled student.
    // Ownership and SUPER_ADMIN checks are inside the service (lazy associations
    // must be accessed within the @Transactional boundary, OSIV is disabled).
    @PreAuthorize("hasRole('FACILITATOR') or hasRole('SUPER_ADMIN')")
    @GetMapping("/{publicId}/attendance")
    public ResponseEntity<List<SessionAttendanceRosterEntry>> getAttendanceRoster(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(sessionService.getAttendanceRoster(publicId, caller));
    }

    // Public (unauthenticated) — resolves a QR token to enough info for a
    // student to confirm they scanned the right session before typing the
    // spoken code. Never returns the session code itself.
    @GetMapping("/qr/{qrToken}")
    public ResponseEntity<SessionQrRouteResponse> getByQrToken(@PathVariable String qrToken) {
        return ResponseEntity.ok(sessionService.getQrRouteInfo(qrToken));
    }
}