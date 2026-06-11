package com.ecommerce.order.domain.port;

import java.util.UUID;

import reactor.core.publisher.Mono;

/**
 * Outbound port to the Notification service (simulated by WireMock from Phase 5). Driven by the
 * outbox poller (ADR-04) when domain events are published.
 */
public interface NotificationPort {

    Mono<Void> send(NotificationMessage message);

    record NotificationMessage(UUID orderId, String type) {
    }
}
