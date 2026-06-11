package com.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentApproved(UUID orderId, UUID paymentId, Instant occurredAt) implements DomainEvent {
}
