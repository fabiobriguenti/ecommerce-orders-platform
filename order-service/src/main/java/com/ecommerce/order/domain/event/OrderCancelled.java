package com.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(UUID orderId, String reason, Instant occurredAt) implements DomainEvent {
}
