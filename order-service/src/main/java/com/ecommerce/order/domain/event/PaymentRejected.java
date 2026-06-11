package com.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentRejected(UUID orderId, UUID paymentId, int attempts, Instant occurredAt)
        implements DomainEvent {
}
