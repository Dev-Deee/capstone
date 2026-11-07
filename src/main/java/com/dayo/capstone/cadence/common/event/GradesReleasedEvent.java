package com.dayo.capstone.cadence.common.event;

import java.util.List;

/**
 * Grades on an assignment became visible to these students: all reviewed
 * students when the facilitator releases grades, or one student whose work
 * is accepted after the release.
 */
public class GradesReleasedEvent extends DomainEvent {

    private final List<Long> studentIds;
    private final String assignmentTitle;

    public GradesReleasedEvent(Long institutionId, Long actorId, List<Long> studentIds, String assignmentTitle) {
        super(institutionId, actorId);
        this.studentIds = List.copyOf(studentIds);
        this.assignmentTitle = assignmentTitle;
    }

    public List<Long> studentIds() { return studentIds; }
    public String assignmentTitle() { return assignmentTitle; }
}
