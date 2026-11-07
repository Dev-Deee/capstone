package com.dayo.capstone.cadence.assignment.model;

import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.enums.SubmissionType;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per student per assignment, ever. Resubmissions update this
 * row in-place (resubmissionCount++), they do not create new rows. The
 * unique constraint on (assignment_id, student_id) enforces this at the
 * database level. submittedAt is null until the first actual submission.
 */
@Entity
@Table(name = "submission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"assignment_id", "student_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Submission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private SubmissionType submissionType;

    private String linkUrl;

    private String fileUrl;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "resubmission_count")
    private int resubmissionCount = 0;

    // Attempts the facilitator granted beyond the assignment's maxResubmissions.
    @Column(name = "extra_attempts", nullable = false, columnDefinition = "integer default 0")
    private int extraAttempts = 0;

    // ── The current review (each one is also kept in submission_attempt) ──
    @Column(precision = 7, scale = 2)
    private BigDecimal score;

    private Boolean passed;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // While REDO_REQUESTED: the student may submit again until this time.
    @Column(name = "redo_due_at")
    private LocalDateTime redoDueAt;

    public int attemptsAllowed() {
        return assignment.getMaxResubmissions() + extraAttempts;
    }
}
