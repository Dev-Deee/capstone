package com.dayo.capstone.cadence.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Something that happened in the domain, published once through Spring's
 * ApplicationEventPublisher (see DomainEvents) and fanned out by independent
 * listeners — notifications today, more channels later — PRD Section 12.
 *
 * A producer publishes the event and knows nothing about who listens.
 * Listeners run after the producer's transaction commits, so a rolled-back
 * change never emits anything.
 *
 * actorId is optional on purpose: some events must not identify who caused
 * them (PRD 18.6 — anonymous feedback never carries a submitter).
 */
public abstract class DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final LocalDateTime occurredAt = LocalDateTime.now();
    private final Long institutionId;
    private final Long actorId;

    protected DomainEvent(Long institutionId, Long actorId) {
        if (institutionId == null) throw new IllegalArgumentException("A domain event belongs to an institution");
        this.institutionId = institutionId;
        this.actorId = actorId;
    }

    /** Unique per event; listeners use it to stay idempotent if an event is delivered twice. */
    public UUID eventId() { return eventId; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public Long institutionId() { return institutionId; }
    /** The user who caused it, or null when there isn't one (or it must not be known). */
    public Long actorId() { return actorId; }
}
