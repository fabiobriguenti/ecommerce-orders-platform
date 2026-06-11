package com.ecommerce.order.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.event.OrderConfirmed;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.infrastructure.persistence.AbstractPostgresIT;
import com.ecommerce.order.infrastructure.persistence.OutboxRow;

class OutboxEventPublisherIT extends AbstractPostgresIT {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void writesDomainEventAsOutboxRow() {
        OutboxEventPublisher publisher = new OutboxEventPublisher(template, objectMapper);
        UUID orderId = UUID.randomUUID();
        OrderConfirmed event = new OrderConfirmed(orderId, CustomerId.of("c1"),
                Money.of("25.50", "BRL"), Instant.now());

        StepVerifier.create(publisher.publish(event)).verifyComplete();

        StepVerifier.create(template.select(OutboxRow.class).all().collectList())
                .assertNext(rows -> {
                    assertThat(rows).hasSize(1);
                    OutboxRow row = rows.get(0);
                    assertThat(row.getType()).isEqualTo("OrderConfirmed");
                    assertThat(row.getAggregateId()).isEqualTo(orderId);
                    assertThat(row.getPayload()).contains(orderId.toString());
                    assertThat(row.getPublishedAt()).isNull();
                })
                .verifyComplete();
    }
}
