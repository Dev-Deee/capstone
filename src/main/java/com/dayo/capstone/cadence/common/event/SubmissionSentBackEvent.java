package com.dayo.capstone.cadence.common.event;

import java.time.LocalDateTime;

/** A facilitator sent a student's work back to be redone. Never held back: the student has to act. */
public class SubmissionSentBackEvent extends DomainEvent {

    private final Long studentId;
    private final String assignmentTitle;
    private final LocalDateTime redoDueAt;

    public SubmissionSentBackEvent(Long institutionId, Long actorId, Long studentId,
                                   String assignmentTitle, LocalDateTime redoDueAt) {
        super(institutionId, actorId);
        this.studentId = studentId;
        this.assignmentTitle = assignmentTitle;
        this.redoDueAt = redoDueAt;
    }

    public Long studentId() { return studentId; }
    public String assignmentTitle() { return assignmentTitle; }
    public LocalDateTime redoDueAt() { return redoDueAt; }
}
