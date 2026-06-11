package com.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for domain events. In Phase 4 these are persisted to the outbox within the same
 * transaction as the aggregate change and later published to the Notification service (ADR-04).
 */
public interface DomainEvent {

    UUID orderId();

    Instant occurredAt();
}
