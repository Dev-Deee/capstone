package com.dayo.capstone.cadence.assignment.model;

import com.dayo.capstone.cadence.assignment.enums.ExtensionRequestStatus;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Extension requests do NOT modify Submission.deadline — the effective
 * deadline is computed on-the-fly wherever needed by looking up the
 * APPROVED ExtensionRequest for the submission (if any) and using its
 * requestedNewDeadline in place of Assignment.deadline. This avoids
 * duplicating deadline data across rows and keeps the single source of
 * truth in the Assignment.
 */
@Entity
@Table(name = "extension_request")
@Getter
@Setter
@NoArgsConstructor
public class ExtensionRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "requested_new_deadline", nullable = false)
    private LocalDateTime requestedNewDeadline;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtensionRequestStatus status = ExtensionRequestStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    private String reviewNote;
}
