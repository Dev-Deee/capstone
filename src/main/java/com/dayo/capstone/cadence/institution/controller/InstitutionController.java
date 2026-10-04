package com.dayo.capstone.cadence.institution.controller;

import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.institution.dto.request.FoundInstitutionRequest;
import com.dayo.capstone.cadence.institution.dto.request.InstitutionSignupRequest;
import com.dayo.capstone.cadence.institution.dto.response.InstitutionResponse;
import com.dayo.capstone.cadence.institution.dto.response.MembershipSummaryResponse;
import com.dayo.capstone.cadence.institution.dto.response.SlugAvailabilityResponse;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.service.InstitutionOnboardingService;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/institutions")
@RequiredArgsConstructor
@Tag(name = "Institutions", description = "Multi-tenant root: sign-up, membership, platform view")
public class InstitutionController {

    private final InstitutionService institutionService;
    private final InstitutionOnboardingService onboardingService;
    private final UserService userService;

    // ── Public ────────────────────────────────────────────────────────────

    // Self-serve: a new person founds a new institution and becomes its SUPER_ADMIN.
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(
            @Valid @RequestBody InstitutionSignupRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(onboardingService.signUp(
                request, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }

    @GetMapping("/slug-availability")
    public ResponseEntity<SlugAvailabilityResponse> slugAvailability(@RequestParam String slug) {
        return ResponseEntity.ok(onboardingService.checkSlug(slug));
    }

    // ── Signed-in users ───────────────────────────────────────────────────

    // An existing user founds an additional institution; the session switches into it.
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<AuthResponse> found(
            @AuthenticationPrincipal CadencePrincipal principal,
            @Valid @RequestBody FoundInstitutionRequest request,
            HttpServletRequest httpRequest
    ) {
        User founder = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED).body(onboardingService.found(
                request, founder, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }

    // Institutions the caller belongs to — the institution switcher.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MembershipSummaryResponse>> mine(@AuthenticationPrincipal CadencePrincipal principal) {
        User user = userService.findEntityByPublicId(principal.userPublicId());
        Long current = TenantContext.institutionId();
        return ResponseEntity.ok(user.getMemberships().stream()
                .sorted(Comparator.comparing(Membership::getCreatedAt))
                .map(m -> {
                    Institution i = m.getInstitution();
                    return new MembershipSummaryResponse(i.getPublicId(), i.getName(), i.getSlug(),
                            m.getRoles().stream().map(Role::name).collect(Collectors.toCollection(java.util.TreeSet::new)),
                            m.isActive(), i.getId().equals(current));
                })
                .toList());
    }

    // The institution this session is acting in.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/current")
    public ResponseEntity<InstitutionResponse> current(@AuthenticationPrincipal CadencePrincipal principal) {
        return ResponseEntity.ok(institutionService.toResponse(
                institutionService.findEntityByUserPublicId(principal.userPublicId())));
    }

    // ── Platform ──────────────────────────────────────────────────────────

    // Every tenant — platform admins only. Institution SUPER_ADMINs see only their own.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping
    public ResponseEntity<List<InstitutionResponse>> getAll() {
        return ResponseEntity.ok(institutionService.getAll());
    }

    // A platform admin may read any institution; anyone else only their current one.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{publicId}")
    public ResponseEntity<InstitutionResponse> getOne(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        if (!principal.platformAdmin() && !publicId.equals(principal.institutionPublicId())) {
            throw new ResourceNotFoundException("Institution not found: " + publicId);
        }
        return ResponseEntity.ok(institutionService.getByPublicId(publicId));
    }
}
