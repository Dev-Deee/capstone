package com.dayo.capstone.cadence.assignment.controller;

import com.dayo.capstone.cadence.assignment.dto.request.RedoExtensionRequest;
import com.dayo.capstone.cadence.assignment.dto.request.RegradeRequest;
import com.dayo.capstone.cadence.assignment.dto.request.ReviewSubmissionRequest;
import com.dayo.capstone.cadence.assignment.dto.response.AssignmentResponse;
import com.dayo.capstone.cadence.assignment.dto.response.ReviewRowResponse;
import com.dayo.capstone.cadence.assignment.service.SubmissionReviewService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.security.CadencePrincipal;
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
import java.util.Map;
import java.util.UUID;

// Facilitator review of submitted work. Ownership (the assignment's own
// facilitator, or a SUPER_ADMIN) is checked in SubmissionReviewService.
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('FACILITATOR')")
@Tag(name = "Review", description = "Reviewing and grading submitted work, redo requests, releasing grades")
public class SubmissionReviewController {

    private final SubmissionReviewService reviewService;
    private final UserService userService;
    private final InstitutionService institutionService;

    @GetMapping("/api/assignments/{publicId}/submissions")
    public ResponseEntity<List<ReviewRowResponse>> list(@AuthenticationPrincipal CadencePrincipal principal,
                                                        @PathVariable UUID publicId) {
        return ResponseEntity.ok(reviewService.listForAssignment(publicId, caller(principal), institution(principal)));
    }

    @PostMapping("/api/assignments/{publicId}/release-grades")
    public ResponseEntity<AssignmentResponse> releaseGrades(@AuthenticationPrincipal CadencePrincipal principal,
                                                            @PathVariable UUID publicId) {
        return ResponseEntity.ok(reviewService.releaseGrades(publicId, caller(principal), institution(principal)));
    }

    @PostMapping("/api/submissions/{publicId}/review")
    public ResponseEntity<ReviewRowResponse> review(@AuthenticationPrincipal CadencePrincipal principal,
                                                    @PathVariable UUID publicId,
                                                    @Valid @RequestBody ReviewSubmissionRequest request) {
        return ResponseEntity.ok(reviewService.review(publicId, request, caller(principal), institution(principal)));
    }

    @PatchMapping("/api/submissions/{publicId}/grade")
    public ResponseEntity<ReviewRowResponse> regrade(@AuthenticationPrincipal CadencePrincipal principal,
                                                     @PathVariable UUID publicId,
                                                     @Valid @RequestBody RegradeRequest request) {
        return ResponseEntity.ok(reviewService.regrade(publicId, request, caller(principal), institution(principal)));
    }

    @PostMapping("/api/submissions/{publicId}/redo-extension")
    public ResponseEntity<ReviewRowResponse> extendRedo(@AuthenticationPrincipal CadencePrincipal principal,
                                                        @PathVariable UUID publicId,
                                                        @RequestBody RedoExtensionRequest request) {
        return ResponseEntity.ok(reviewService.extendRedo(publicId, request, caller(principal), institution(principal)));
    }

    @GetMapping("/api/submissions/waiting-count")
    public ResponseEntity<Map<String, Long>> waitingCount(@AuthenticationPrincipal CadencePrincipal principal) {
        return ResponseEntity.ok(Map.of("waiting", reviewService.waitingCount(caller(principal))));
    }

    private User caller(CadencePrincipal principal) {
        return userService.findEntityByPublicId(principal.userPublicId());
    }

    private Institution institution(CadencePrincipal principal) {
        return institutionService.findEntityByUserPublicId(principal.userPublicId());
    }
}
