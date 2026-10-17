package com.dayo.capstone.cadence.user.controller;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.dto.request.UpdateRolesRequest;
import com.dayo.capstone.cadence.user.dto.request.UpdateUserRequest;
import com.dayo.capstone.cadence.user.dto.response.UserResponse;
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
    private final InstitutionService institutionService;

    // A user may always view their own record.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getSelf(@AuthenticationPrincipal CadencePrincipal principal) {
        return ResponseEntity.ok(userService.getByPublicId(principal.userPublicId()));
    }

    // Viewing another specific user is admin-only — a student has no
    // legitimate reason to look up an arbitrary facilitator's record by ID.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/{publicId}")
    public ResponseEntity<UserResponse> getOne(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_USERS);
        }
        userService.findInCallerInstitution(publicId, caller); // 404 outside the caller's institution
        return ResponseEntity.ok(userService.getByPublicId(publicId));
    }

    // Listing the full user directory is admin-only.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermission(caller, Permission.MANAGE_USERS);
        }
        return ResponseEntity.ok(userService.getAll(
                institutionService.findEntityByUserPublicId(principal.userPublicId())));
    }

    // User management. Who may act on whom (SUPER_ADMIN / ADMIN with MANAGE_USERS /
    // FACILITATOR for their own students), institution scoping, self/super-admin
    // guards and audit logging all live in UserService.
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PatchMapping("/{publicId}")
    public ResponseEntity<UserResponse> update(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(userService.updateByAdmin(publicId, request, caller));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('FACILITATOR')")
    @PatchMapping("/{publicId}/activate")
    public ResponseEntity<UserResponse> activate(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(userService.setActive(publicId, true, caller));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('FACILITATOR')")
    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<UserResponse> deactivate(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(userService.setActive(publicId, false, caller));
    }

    // Removes the user from the caller's institution only — their account and
    // any other institutions they belong to are untouched (multi-tenant).
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> removeFromInstitution(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        userService.removeFromInstitution(publicId, caller);
        return ResponseEntity.noContent().build();
    }

    // Owners only: replace someone's roles in this institution. Refuses to
    // remove the last SUPER_ADMIN.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{publicId}/roles")
    public ResponseEntity<UserResponse> updateRoles(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @Valid @RequestBody UpdateRolesRequest request
    ) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(userService.updateRoles(publicId, request.getRoles(), caller));
    }

    // The caller leaves the institution they're working in (refused for the last SUPER_ADMIN).
    @PostMapping("/me/leave-institution")
    public ResponseEntity<Void> leaveInstitution(@AuthenticationPrincipal CadencePrincipal principal) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        userService.leaveCurrentInstitution(caller);
        return ResponseEntity.noContent().build();
    }

    // A user may delete (soft-delete/anonymize) their own account everywhere —
    // refused while they are the only SUPER_ADMIN of any institution.
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

    // A facilitator's roster: students actively enrolled in cohorts they teach.
    @PreAuthorize("hasRole('FACILITATOR')")
    @GetMapping("/my-students")
    public ResponseEntity<List<UserResponse>> getMyStudents(@AuthenticationPrincipal CadencePrincipal principal) {
        User caller = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(userService.getStudentsForFacilitator(caller));
    }

    // No @PreAuthorize — same open pattern as SubjectController.getAll()
    // and CohortController.getAll(): any signed-in user, but only their own
    // institution's active facilitators, for populating dropdowns.
    @GetMapping("/facilitators")
    public ResponseEntity<List<UserResponse>> getFacilitators(@AuthenticationPrincipal CadencePrincipal principal) {
        return ResponseEntity.ok(userService.getFacilitators(
                institutionService.findEntityByUserPublicId(principal.userPublicId())));
    }
}