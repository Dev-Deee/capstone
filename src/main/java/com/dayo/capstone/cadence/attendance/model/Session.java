package com.dayo.capstone.cadence.attendance.model;

import com.dayo.capstone.cadence.attendance.enums.SessionClosedBy;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PRD Section 6 — deliberately scoped to session-code verification only
 * for this pass. Hall/GPS reference points (Section 6.4), QR mode,
 * heartbeat (6.9), and device fingerprinting (6.10) layer in once this
 * core flow is proven end to end — not fields on this entity yet.
 */
@Entity
@Table(name = "session")
@Getter
@Setter
@NoArgsConstructor
public class Session extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilitator_id", nullable = false)
    private User facilitator;

    @Column(name = "session_code", nullable = false)
    private String sessionCode;

    // TOTP secret — hex-encoded, generated once in open(), never rotated.
    // The rotating 6-digit code is derived from this on every request;
    // sessionCode is kept for legacy display only and is no longer validated.
    @Column(name = "code_secret")
    private String codeSecret;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private boolean isOpen = true;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_by")
    private SessionClosedBy closedBy;

    @Column(name = "qr_token", nullable = false, unique = true)
    private String qrToken;

    public boolean isCurrentlyOpen() {
        return isOpen && closedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}