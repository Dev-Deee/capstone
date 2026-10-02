package com.dayo.capstone.cadence.institution.controller;

import com.dayo.capstone.cadence.institution.dto.request.CreateInstitutionRequest;
import com.dayo.capstone.cadence.institution.dto.response.InstitutionResponse;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/institutions")
@RequiredArgsConstructor
@Tag(name = "Institutions", description = "PRD Sections 4, 5 — root of the multi-tenant schema")
public class InstitutionController {

    private final InstitutionService institutionService;

    // Institution creation is SUPER_ADMIN only — even an ADMIN at an
    // existing institution has no business creating a new one.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<InstitutionResponse> create(@Valid @RequestBody CreateInstitutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(institutionService.create(request));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{publicId}")
    public ResponseEntity<InstitutionResponse> getOne(@PathVariable UUID publicId) {
        return ResponseEntity.ok(institutionService.getByPublicId(publicId));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<InstitutionResponse>> getAll() {
        return ResponseEntity.ok(institutionService.getAll());
    }
}