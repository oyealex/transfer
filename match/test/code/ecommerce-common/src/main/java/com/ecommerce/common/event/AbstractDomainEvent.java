package com.ecommerce.common.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all domain events in the ShopHub system.
 * Extends Spring's ApplicationEvent for integration with the Spring event bus.
 */
public abstract class AbstractDomainEvent extends ApplicationEvent {

    private final String eventId;
    private final LocalDateTime occurredAt;

    public AbstractDomainEvent(Object source) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
