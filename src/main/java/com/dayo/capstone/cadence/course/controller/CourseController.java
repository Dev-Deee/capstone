package com.dayo.capstone.cadence.course.controller;

import com.dayo.capstone.cadence.course.dto.CourseRequest;
import com.dayo.capstone.cadence.course.dto.CourseResponse;
import com.dayo.capstone.cadence.course.dto.LinkMaterialRequest;
import com.dayo.capstone.cadence.course.dto.MaterialUpdateRequest;
import com.dayo.capstone.cadence.course.dto.MoveRequest;
import com.dayo.capstone.cadence.course.dto.SectionRequest;
import com.dayo.capstone.cadence.course.service.CourseService;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Course materials. Who may edit what is decided in CourseService; students
// read only what is published to them.
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Courses", description = "Course materials: sections of files and links for cohorts or all students")
public class CourseController {

    private final CourseService courseService;
    private final UserService userService;
    private final InstitutionService institutionService;

    // ── Reading ──

    @GetMapping("/api/courses/manage")
    public ResponseEntity<List<CourseResponse>> manageable(@AuthenticationPrincipal CadencePrincipal p) {
        return ResponseEntity.ok(courseService.listManageable(caller(p), institution(p)));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/courses/mine")
    public ResponseEntity<List<CourseResponse>> mine(@AuthenticationPrincipal CadencePrincipal p) {
        return ResponseEntity.ok(courseService.listForStudent(caller(p), institution(p)));
    }

    @GetMapping("/api/courses/{publicId}")
    public ResponseEntity<CourseResponse> get(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(courseService.get(publicId, caller(p), institution(p)));
    }

    // ── Courses ──

    @PostMapping("/api/courses")
    public ResponseEntity<CourseResponse> create(@AuthenticationPrincipal CadencePrincipal p, @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(request, caller(p), institution(p)));
    }

    @PutMapping("/api/courses/{publicId}")
    public ResponseEntity<CourseResponse> update(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                 @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.update(publicId, request, caller(p), institution(p)));
    }

    @DeleteMapping("/api/courses/{publicId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        courseService.delete(publicId, caller(p), institution(p));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/courses/{publicId}/publish")
    public ResponseEntity<CourseResponse> publish(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(courseService.publish(publicId, caller(p), institution(p)));
    }

    @PostMapping("/api/courses/{publicId}/unpublish")
    public ResponseEntity<CourseResponse> unpublish(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(courseService.unpublish(publicId, caller(p), institution(p)));
    }

    // Publish later: {"publishAt": "2026-11-20T09:00:00"}, or null to unschedule.
    @PatchMapping("/api/courses/{publicId}/schedule")
    public ResponseEntity<CourseResponse> schedule(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                   @RequestBody Map<String, String> body) {
        String raw = body.get("publishAt");
        LocalDateTime publishAt = raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw);
        return ResponseEntity.ok(courseService.schedule(publicId, publishAt, caller(p), institution(p)));
    }

    // ── Sections ──

    @PostMapping("/api/courses/{publicId}/sections")
    public ResponseEntity<CourseResponse> addSection(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                     @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.ok(courseService.addSection(publicId, request.getTitle(), caller(p), institution(p)));
    }

    @PatchMapping("/api/course-sections/{publicId}")
    public ResponseEntity<CourseResponse> renameSection(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                        @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.ok(courseService.renameSection(publicId, request.getTitle(), caller(p), institution(p)));
    }

    @DeleteMapping("/api/course-sections/{publicId}")
    public ResponseEntity<CourseResponse> deleteSection(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(courseService.deleteSection(publicId, caller(p), institution(p)));
    }

    @PostMapping("/api/course-sections/{publicId}/move")
    public ResponseEntity<CourseResponse> moveSection(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                      @Valid @RequestBody MoveRequest request) {
        return ResponseEntity.ok(courseService.moveSection(publicId, request.getDirection(), caller(p), institution(p)));
    }

    // ── Materials ──

    @PostMapping(value = "/api/course-sections/{publicId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseResponse> addFile(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                  @RequestParam MultipartFile file,
                                                  @RequestParam(required = false) String title,
                                                  @RequestParam(required = false) String note) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload");
        if (file.getSize() > CourseService.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Files can be at most 100 MB. Use a Google Drive link for bigger ones.");
        }
        return ResponseEntity.ok(courseService.addFile(publicId, title, note, file.getBytes(),
                file.getOriginalFilename(), file.getContentType(), caller(p), institution(p)));
    }

    @PostMapping("/api/course-sections/{publicId}/links")
    public ResponseEntity<CourseResponse> addLink(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                  @Valid @RequestBody LinkMaterialRequest request) {
        return ResponseEntity.ok(courseService.addLink(publicId, request, caller(p), institution(p)));
    }

    @PatchMapping("/api/course-materials/{publicId}")
    public ResponseEntity<CourseResponse> updateMaterial(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                         @Valid @RequestBody MaterialUpdateRequest request) {
        return ResponseEntity.ok(courseService.updateMaterial(publicId, request, caller(p), institution(p)));
    }

    @DeleteMapping("/api/course-materials/{publicId}")
    public ResponseEntity<CourseResponse> deleteMaterial(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId) {
        return ResponseEntity.ok(courseService.deleteMaterial(publicId, caller(p), institution(p)));
    }

    @PostMapping("/api/course-materials/{publicId}/move")
    public ResponseEntity<CourseResponse> moveMaterial(@AuthenticationPrincipal CadencePrincipal p, @PathVariable UUID publicId,
                                                       @Valid @RequestBody MoveRequest request) {
        return ResponseEntity.ok(courseService.moveMaterial(publicId, request.getDirection(), caller(p), institution(p)));
    }

    private User caller(CadencePrincipal p) {
        return userService.findEntityByPublicId(p.userPublicId());
    }

    private Institution institution(CadencePrincipal p) {
        return institutionService.findEntityByUserPublicId(p.userPublicId());
    }
}
