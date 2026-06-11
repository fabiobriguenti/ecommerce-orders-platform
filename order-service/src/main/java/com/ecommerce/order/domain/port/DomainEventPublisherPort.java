package com.ecommerce.order.domain.port;

import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.event.DomainEvent;

/**
 * Outbound port for publishing domain events. The Phase 4 adapter appends them to the outbox
 * table within the same transaction as the aggregate change (ADR-04); the outbox poller later
 * delivers them to the Notification service.
 */
public interface DomainEventPublisherPort {

    Mono<Void> publish(DomainEvent event);
}
