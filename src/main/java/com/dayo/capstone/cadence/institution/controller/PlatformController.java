package com.dayo.capstone.cadence.institution.controller;

import com.dayo.capstone.cadence.institution.dto.request.ChangeInstitutionStatusRequest;
import com.dayo.capstone.cadence.institution.dto.response.PlatformInstitutionRow;
import com.dayo.capstone.cadence.institution.dto.response.PlatformOverview;
import com.dayo.capstone.cadence.institution.service.PlatformService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Platform admins only: the cross-institution console.
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "Platform", description = "Platform admin console — every institution, and their status")
public class PlatformController {

    private final PlatformService platformService;
    private final UserService userService;

    @GetMapping("/overview")
    public ResponseEntity<PlatformOverview> overview() {
        return ResponseEntity.ok(platformService.overview());
    }

    @GetMapping("/institutions")
    public ResponseEntity<List<PlatformInstitutionRow>> institutions() {
        return ResponseEntity.ok(platformService.institutions());
    }

    @PatchMapping("/institutions/{publicId}/status")
    public ResponseEntity<PlatformInstitutionRow> changeStatus(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @Valid @RequestBody ChangeInstitutionStatusRequest request
    ) {
        return ResponseEntity.ok(platformService.changeStatus(
                publicId, request, userService.findEntityByPublicId(principal.userPublicId())));
    }
}
