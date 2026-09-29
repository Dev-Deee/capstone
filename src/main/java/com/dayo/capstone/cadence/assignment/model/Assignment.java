package com.dayo.capstone.cadence.assignment.model;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
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
 * isLocked is set true the moment any student first submits against this
 * assignment — it protects students from having the assignment content
 * changed under them after they've already started working on it.
 * clarificationNote is the one field that remains editable after locking,
 * so the facilitator can still add context without altering the original
 * problem statement.
 */
@Entity
@Table(name = "assignment")
@Getter
@Setter
@NoArgsConstructor
public class Assignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilitator_id", nullable = false)
    private User facilitator;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(nullable = false)
    private LocalDateTime deadline;

    private int maxResubmissions = 3;

    private boolean isLocked = false;

    private String clarificationNote;
}
