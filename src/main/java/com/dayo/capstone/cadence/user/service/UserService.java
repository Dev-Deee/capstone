package com.dayo.capstone.cadence.user.service;

import com.dayo.capstone.cadence.common.service.FileStorageService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.InstitutionService;
import com.dayo.capstone.cadence.user.dto.request.CreateUserRequest;
import com.dayo.capstone.cadence.user.dto.response.UserResponse;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InstitutionService institutionService;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService; // add to injection

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        Institution institution = institutionService
                .findEntityByPublicId(UUID.fromString(request.getInstitutionPublicId()));

        User user = new User();
        user.setInstitution(institution);
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(parseRoles(request.getRoles()));

        User saved = userRepository.save(user);
        log.info("User created: publicId={}, roles={}", saved.getPublicId(), saved.getRoles());

        return toResponse(saved);
    }


    @Transactional
    public UserResponse updateProfileImage(UUID userPublicId, byte[] imageBytes, String filename) {
        User user = findEntityByPublicId(userPublicId);

        // Delete the old image first if one exists — avoids orphaned files
        // accumulating in Cloudinary storage every time a user updates their photo.
        if (user.getProfileImageUrl() != null) {
            fileStorageService.delete(user.getProfileImageUrl());
        }

        String url = fileStorageService.upload(imageBytes, filename, "profile-images");
        user.setProfileImageUrl(url);
        userRepository.save(user);

        log.info("Profile image updated: userPublicId={}", userPublicId);
        return toResponse(user);
    }

    public UserResponse getByPublicId(UUID publicId) {
        return toResponse(findEntityByPublicId(publicId));
    }

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .map(this::toResponse)
                .toList();
    }

    public User findEntityByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + publicId));
    }

    /**
     * PRD Section 18.1 — right-to-erasure via soft-delete/anonymization,
     * never a hard delete. PII is nulled; historical records (AttendanceRecord,
     * Submission, AuditLog) referencing this user's internal id stay intact
     * for aggregate/audit integrity.
     */
    @Transactional
    public void softDeleteAndAnonymize(UUID publicId) {
        User user = findEntityByPublicId(publicId);
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setMiddleName(null);
        user.setEmail("deleted-" + user.getId() + "@anonymized.local");
        user.setPhoneNumber(null);
        user.setPasswordHash(null);
        user.setDeleted(true);
        userRepository.save(user);
        log.info("User soft-deleted and anonymized: publicId={}", publicId);
    }

    private Set<Role> parseRoles(Set<String> roleStrings) {
        return roleStrings.stream()
                .map(String::toUpperCase)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .publicId(user.getPublicId())
                .institutionPublicId(user.getInstitution().getPublicId())
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .roles(user.getRoles())
                .consentGiven(user.isConsentGiven())
                .webAuthnCapable(user.isWebAuthnCapable())
                .createdAt(user.getCreatedAt())
                .build();
    }
}