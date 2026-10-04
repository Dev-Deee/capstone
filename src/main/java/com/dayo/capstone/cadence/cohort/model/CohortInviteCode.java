package com.dayo.capstone.cadence.cohort.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One code per cohort. Institution resolves transitively via
 * cohort.institution — a student registering into a cohort automatically
 * lands in the right institution, no separate lookup needed.
 *
 * Self-registration via this code always produces STUDENT accounts —
 * no assignedRole field exists here. FACILITATOR/ADMIN/SUPER_ADMIN
 * accounts are never self-registered; staff join through the
 * staff-invite flow.
 */
@Entity
@Table(name = "cohort_invite_code")
@Getter
@Setter
@NoArgsConstructor
public class CohortInviteCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(unique = true, nullable = false)
    private String code;

    private LocalDateTime expiresAt;

    private boolean revoked = false;

    private int maxUses;

    private int useCount = 0;

    public boolean isValid() {
        return !revoked
                && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()))
                && (maxUses == 0 || useCount < maxUses);
    }
}