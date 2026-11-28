package com.dayo.capstone.cadence.common.event;

/** The morning summary: handed-in work waiting for this facilitator's review here. No actor: a job sends it. */
public class ReviewsWaitingDigestEvent extends DomainEvent {

    private final Long facilitatorId;
    private final long submissions;
    private final long assignments;

    public ReviewsWaitingDigestEvent(Long institutionId, Long facilitatorId, long submissions, long assignments) {
        super(institutionId, null);
        this.facilitatorId = facilitatorId;
        this.submissions = submissions;
        this.assignments = assignments;
    }

    public Long facilitatorId() { return facilitatorId; }
    public long submissions() { return submissions; }
    public long assignments() { return assignments; }
}
