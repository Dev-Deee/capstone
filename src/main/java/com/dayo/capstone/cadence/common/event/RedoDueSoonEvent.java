package com.dayo.capstone.cadence.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

/** Work sent back for a redo is due again soon and the student hasn't handed it in. No actor: a job sends it. */
public class RedoDueSoonEvent extends DomainEvent {

    private final Long studentId;
    private final String assignmentTitle;
    private final UUID assignmentPublicId;
    private final LocalDateTime redoDueAt;

    public RedoDueSoonEvent(Long institutionId, Long studentId, String assignmentTitle,
                            UUID assignmentPublicId, LocalDateTime redoDueAt) {
        super(institutionId, null);
        this.studentId = studentId;
        this.assignmentTitle = assignmentTitle;
        this.assignmentPublicId = assignmentPublicId;
        this.redoDueAt = redoDueAt;
    }

    public Long studentId() { return studentId; }
    public String assignmentTitle() { return assignmentTitle; }
    public UUID assignmentPublicId() { return assignmentPublicId; }
    public LocalDateTime redoDueAt() { return redoDueAt; }
}
