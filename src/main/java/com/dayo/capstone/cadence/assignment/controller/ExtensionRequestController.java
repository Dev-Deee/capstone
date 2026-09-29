package com.dayo.capstone.cadence.assignment.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.assignment.dto.request.ReviewExtensionRequest;
import com.dayo.capstone.cadence.assignment.dto.response.ExtensionRequestResponse;
import com.dayo.capstone.cadence.assignment.service.ExtensionRequestService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
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

@RestController
@RequestMapping("/api/extension-requests")
@RequiredArgsConstructor
@Tag(name = "Extension Requests", description = "Facilitator review of student deadline-extension requests")
public class ExtensionRequestController {

    private final ExtensionRequestService extensionRequestService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('FACILITATOR')")
    @PatchMapping("/{publicId}/review")
    public ResponseEntity<ExtensionRequestResponse> review(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @Valid @RequestBody ReviewExtensionRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN) && !caller.hasRole(Role.FACILITATOR)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_ASSIGNMENTS);
        }
        return ResponseEntity.ok(extensionRequestService.reviewExtension(publicId, caller, request));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('FACILITATOR')")
    @GetMapping("/pending")
    public ResponseEntity<List<ExtensionRequestResponse>> getPending(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(extensionRequestService.getPendingForFacilitator(caller));
    }
}
