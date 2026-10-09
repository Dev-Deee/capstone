package com.dayo.capstone.cadence.common.event;

import java.util.UUID;

/** An admin enrolled a student in a cohort, or moved them from one cohort to another. */
public class StudentCohortChangedEvent extends DomainEvent {

    private final Long studentId;
    private final String fromCohortName; // null when newly enrolled
    private final String toCohortName;
    private final UUID toCohortPublicId;

    public StudentCohortChangedEvent(Long institutionId, Long actorId, Long studentId,
                                     String fromCohortName, String toCohortName, UUID toCohortPublicId) {
        super(institutionId, actorId);
        this.studentId = studentId;
        this.fromCohortName = fromCohortName;
        this.toCohortName = toCohortName;
        this.toCohortPublicId = toCohortPublicId;
    }

    public Long studentId() { return studentId; }
    public String fromCohortName() { return fromCohortName; }
    public String toCohortName() { return toCohortName; }
    public UUID toCohortPublicId() { return toCohortPublicId; }
}
