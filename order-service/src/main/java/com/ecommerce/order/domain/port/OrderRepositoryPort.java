package com.ecommerce.order.domain.port;

import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.vo.CustomerId;

public interface OrderRepositoryPort {

    Mono<Order> save(Order order);

    Mono<Order> findById(UUID id);

    Flux<Order> findByCustomerId(CustomerId customerId);

    /** Supports the "at most one active order per customer" rule (enforced in CreateOrder). */
    Mono<Boolean> existsActiveByCustomerId(CustomerId customerId);
}
