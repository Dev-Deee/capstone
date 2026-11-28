package com.dayo.capstone.cadence.messaging.controller;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.messaging.dto.AnnouncementPage;
import com.dayo.capstone.cadence.messaging.dto.AnnouncementRequest;
import com.dayo.capstone.cadence.messaging.dto.AnnouncementResponse;
import com.dayo.capstone.cadence.messaging.service.AnnouncementService;
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

// Cohort announcements: staff post, enrolled students read. Who may post to
// which cohort is decided in AnnouncementService.
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Announcements", description = "One-way announcements from staff to cohorts")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;
    private final InstitutionService institutionService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/mine")
    public ResponseEntity<AnnouncementPage> feed(@AuthenticationPrincipal CadencePrincipal p,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(announcementService.feed(caller(p), institution(p), page, size));
    }

    @PreAuthorize("hasAnyRole('FACILITATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/sent")
    public ResponseEntity<AnnouncementPage> sent(@AuthenticationPrincipal CadencePrincipal p,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(announcementService.sent(caller(p), institution(p), page, size));
    }

    // Cohorts the caller may post to (empty for students).
    @GetMapping("/audiences")
    public ResponseEntity<List<AnnouncementResponse.CohortRef>> audiences(@AuthenticationPrincipal CadencePrincipal p) {
        return ResponseEntity.ok(announcementService.audiences(caller(p), institution(p)));
    }

    @PreAuthorize("hasAnyRole('FACILITATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<AnnouncementResponse> post(@AuthenticationPrincipal CadencePrincipal p,
                                                     @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcementService.post(request, caller(p), institution(p)));
    }

    @PreAuthorize("hasAnyRole('FACILITATOR', 'ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        announcementService.delete(publicId, caller(p), institution(p));
        return ResponseEntity.noContent().build();
    }

    private User caller(CadencePrincipal p) {
        return userService.findEntityByPublicId(p.userPublicId());
    }

    private Institution institution(CadencePrincipal p) {
        return institutionService.findEntityByUserPublicId(p.userPublicId());
    }
}
