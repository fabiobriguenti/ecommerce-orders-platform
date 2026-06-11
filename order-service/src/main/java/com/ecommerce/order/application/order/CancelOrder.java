package com.ecommerce.order.application.order;

import java.time.Instant;
import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.domain.event.OrderCancelled;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;

/**
 * Cancels an order (only allowed before payment approval). Idempotent: cancelling an already
 * cancelled order is a no-op and does not re-publish the event.
 */
public class CancelOrder {

    private final OrderRepositoryPort orderRepository;
    private final DomainEventPublisherPort eventPublisher;

    public CancelOrder(OrderRepositoryPort orderRepository, DomainEventPublisherPort eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Order> handle(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    if (order.status() == OrderStatus.CANCELLED) {
                        return Mono.just(order);
                    }
                    order.cancel();
                    return orderRepository.save(order)
                            .flatMap(saved -> eventPublisher
                                    .publish(new OrderCancelled(saved.id(), "CANCELLED_BY_USER", Instant.now()))
                                    .thenReturn(saved));
                });
    }
}
