package com.dayo.capstone.cadence.assignment.controller;

import com.dayo.capstone.cadence.assignment.dto.response.SubmissionResponse;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.assignment.service.SubmissionService;
import com.dayo.capstone.cadence.assignment.service.ExtensionRequestService;
import com.dayo.capstone.cadence.assignment.dto.request.CreateExtensionRequestRequest;
import com.dayo.capstone.cadence.assignment.dto.response.ExtensionRequestResponse;
import com.dayo.capstone.cadence.security.CadencePrincipal;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Tag(name = "Submissions", description = "Student submission flow — submit, re-submit, and extension requests")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final ExtensionRequestService extensionRequestService;
    private final UserService userService;

    // Accepts multipart/form-data for both LINK and FILE submission types:
    //   submissionType = LINK  →  provide linkUrl as a form field
    //   submissionType = FILE  →  provide file as a multipart part
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(value = "/{publicId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submit(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @RequestParam SubmissionType submissionType,
            @RequestParam(required = false) String linkUrl,
            @RequestParam(required = false) MultipartFile file
    ) throws IOException {
        if (submissionType == SubmissionType.FILE) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("A file is required for FILE submissions");
            }
            if (file.getSize() > 50L * 1024 * 1024) {
                throw new IllegalArgumentException("Submission file must be under 50MB");
            }
        }

        User student = userService.findEntityByPublicId(principal.userPublicId());
        byte[] fileBytes = (file != null && !file.isEmpty()) ? file.getBytes() : null;
        String originalFilename = (file != null) ? file.getOriginalFilename() : null;

        return ResponseEntity.ok(submissionService.submit(
                publicId, student, submissionType, linkUrl, fileBytes, originalFilename));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/mine")
    public ResponseEntity<List<SubmissionResponse>> getMine(
            @AuthenticationPrincipal CadencePrincipal principal
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.ok(submissionService.getMine(student));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{publicId}/extension-requests")
    public ResponseEntity<ExtensionRequestResponse> requestExtension(
            @AuthenticationPrincipal CadencePrincipal principal,
            @PathVariable UUID publicId,
            @Valid @RequestBody CreateExtensionRequestRequest request
    ) {
        User student = userService.findEntityByPublicId(principal.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(extensionRequestService.requestExtension(publicId, student, request));
    }
}
