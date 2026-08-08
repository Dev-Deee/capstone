package com.dayo.capstone.cadence.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared base for every entity in the platform.
 *
 * IDOR protection (Insecure Direct Object Reference): the internal `id`
 * is a sequential Long, used only for JPA relationships and joins — it
 * NEVER leaves this application. Every external-facing interaction
 * (URLs, DTOs, API responses) uses `publicId` instead, a non-sequential
 * UUID that cannot be enumerated or guessed.
 *
 * Rule: services and controllers work with `publicId` exclusively.
 * The internal `id` is a repository/JPA implementation detail only.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", updatable = false, nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}