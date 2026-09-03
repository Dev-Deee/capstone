package com.dayo.capstone.cadence.audit.model;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PRD Section 9 — indefinite retention, never purged. actorId is nullable
 * for SYSTEM-triggered actions (scheduled jobs). actorRole is a snapshot
 * at the time of action, not a live lookup — roles can change later.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "actor_public_id")
    private String actorPublicId; // nullable — null means SYSTEM

    @Column(name = "actor_role")
    private String actorRole;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_public_id")
    private String entityPublicId;

    @Lob
    @Column(name = "before_state")
    private String beforeState; // JSON string, nullable

    @Lob
    @Column(name = "after_state")
    private String afterState; // JSON string, nullable

    @Column(name = "ip_address")
    private String ipAddress; // nullable
}