package com.dayo.capstone.cadence.cohort.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PRD Section 18.2/18.3 — enrolledAt is what "late-joiner" attendance and
 * assignment rate calculations key off: sessions/assignments prior to
 * this timestamp are excluded from the student's denominator, never
 * counted as absences/missing work they had no way to complete.
 *
 * isActive supports the offboarding case (Section 18.3) — a student
 * removed from a cohort keeps their historical records intact, but is
 * excluded from active cohort-wide statistics going forward.
 */
@Entity
@Table(name = "cohort_enrollment", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "cohort_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class CohortEnrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    private boolean isActive = true;
}