package com.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;

public record OrderConfirmed(UUID orderId, CustomerId customerId, Money total, Instant occurredAt)
        implements DomainEvent {
}
