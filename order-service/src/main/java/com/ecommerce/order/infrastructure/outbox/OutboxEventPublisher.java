package com.ecommerce.order.infrastructure.outbox;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.event.DomainEvent;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.infrastructure.persistence.OutboxRow;

/**
 * Writes domain events to the transactional outbox (ADR-04). When invoked inside a use case that
 * runs in a transaction (wired in Phase 6), the event row is committed atomically with the
 * aggregate change. The outbox poller (Phase 5) delivers the rows to the Notification service.
 */
@Component
public class OutboxEventPublisher implements DomainEventPublisherPort {

    private final R2dbcEntityTemplate template;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(R2dbcEntityTemplate template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(DomainEvent event) {
        return Mono.fromCallable(() -> serialize(event))
                .flatMap(payload -> {
                    OutboxRow row = new OutboxRow();
                    row.setId(UUID.randomUUID());
                    row.setAggregateId(event.orderId());
                    row.setType(event.getClass().getSimpleName());
                    row.setPayload(payload);
                    row.setCreatedAt(Instant.now());
                    return template.insert(row);
                })
                .then();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event " + event.getClass().getSimpleName(), e);
        }
    }
}
