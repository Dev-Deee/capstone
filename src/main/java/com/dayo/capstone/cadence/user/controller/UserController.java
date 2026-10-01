package com.dayo.capstone.cadence.user.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.dto.response.UserResponse;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "PRD Section 3 — multi-role users; Sections 9, 6.5, 6.12 — consent")
public class UserController {

    private final UserService userService;
    private final AdminPermissionService adminPermissionService;

    // A user may always view their own record.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getSelf(@AuthenticationPrincipal CadencePrincipal principal) {
        return ResponseEntity.ok(userService.getByPublicId(principal.userPublicId()));
    }

    // Viewing another specific user is admin-only — a student has no
    // legitimate reason to look up an arbitrary facilitator's record by ID.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{publicId}")
    public ResponseEntity<UserResponse> getOne(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_USERS);
        }
        return ResponseEntity.ok(userService.getByPublicId(publicId));
    }

    // Listing the full user directory is admin-only.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_USERS);
        }
        return ResponseEntity.ok(userService.getAll());
    }

    // A user may delete (soft-delete/anonymize) their own account.
    // Admin-triggered deletion of ANOTHER user is a separate, deliberately
    // absent capability for now — removing a facilitator/student account
    // administratively deserves its own reviewed flow, not a side effect
    // of reusing this endpoint with a role check bolted on.
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteSelf(@AuthenticationPrincipal CadencePrincipal principal) {
        userService.softDeleteAndAnonymize(principal.userPublicId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/profile-image")
    public ResponseEntity<UserResponse> updateProfileImage(
            @AuthenticationPrincipal CadencePrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // Basic validation — real type/size enforcement matching Section 4.1's
        // pattern belongs here before this goes further; kept minimal for now.
        if (!file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Profile image must be an image file");
        }
        if (file.getSize() > 5 * 1024 * 1024) { // 5MB cap
            throw new IllegalArgumentException("Profile image must be under 5MB");
        }

        UserResponse response = userService.updateProfileImage(
                principal.userPublicId(), file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok(response);
    }

    // No @PreAuthorize — same open pattern as SubjectController.getAll()
    // and CohortController.getAll(). Returns only facilitators, and only
    // the fields needed to populate a dropdown — not the full user
    // directory getAll() exposes.
    @GetMapping("/facilitators")
    public ResponseEntity<List<UserResponse>> getFacilitators() {
        return ResponseEntity.ok(userService.getFacilitators());
    }
}