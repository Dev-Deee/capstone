package com.dayo.capstone.cadence.cohort.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A facilitator is assigned per SUBJECT within a cohort, not just per
 * cohort — this is what allows one facilitator to teach multiple
 * subjects to the same cohort (multiple rows, same facilitator+cohort,
 * different subject), and what allows different facilitators to each
 * own a different subject within one cohort.
 *
 * The unique constraint is now three-way: a facilitator can't be
 * double-assigned to the SAME subject within the SAME cohort twice,
 * but nothing stops them holding several distinct subject assignments
 * in that same cohort.
 */
@Entity
@Table(name = "facilitator_cohort", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"facilitator_id", "cohort_id", "subject_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class FacilitatorCohort extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilitator_id", nullable = false)
    private User facilitator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    private boolean isActive = true;
}