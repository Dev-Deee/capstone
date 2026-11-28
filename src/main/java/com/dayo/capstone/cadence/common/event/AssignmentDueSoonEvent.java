package com.dayo.capstone.cadence.common.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** An open assignment is due soon and these students haven't handed anything in. No actor: a job sends it. */
public class AssignmentDueSoonEvent extends DomainEvent {

    private final List<Long> studentIds;
    private final String assignmentTitle;
    private final UUID assignmentPublicId;
    private final LocalDateTime deadline;

    public AssignmentDueSoonEvent(Long institutionId, List<Long> studentIds, String assignmentTitle,
                                  UUID assignmentPublicId, LocalDateTime deadline) {
        super(institutionId, null);
        this.studentIds = List.copyOf(studentIds);
        this.assignmentTitle = assignmentTitle;
        this.assignmentPublicId = assignmentPublicId;
        this.deadline = deadline;
    }

    public List<Long> studentIds() { return studentIds; }
    public String assignmentTitle() { return assignmentTitle; }
    public UUID assignmentPublicId() { return assignmentPublicId; }
    public LocalDateTime deadline() { return deadline; }
}
