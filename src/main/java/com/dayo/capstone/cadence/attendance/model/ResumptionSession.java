package com.dayo.capstone.cadence.attendance.model;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.attendance.enums.SessionClosedBy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resumption_session", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cohort_id", "resumption_date"})
})
@Getter
@Setter
@NoArgsConstructor
public class ResumptionSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "resumption_date", nullable = false)
    private LocalDate resumptionDate;

    @Column(name = "resumption_code", nullable = false)
    private String resumptionCode;

    @Column(name = "qr_token", nullable = false, unique = true)
    private String qrToken;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private boolean isOpen = true;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_by")
    private SessionClosedBy closedBy;

    public boolean isCurrentlyOpen() {
        return isOpen && closedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}