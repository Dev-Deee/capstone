package com.dayo.capstone.cadence.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** The one way producers publish: a thin, typed wrapper over ApplicationEventPublisher. */
@Component
@RequiredArgsConstructor
public class DomainEvents {

    private final ApplicationEventPublisher publisher;

    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
