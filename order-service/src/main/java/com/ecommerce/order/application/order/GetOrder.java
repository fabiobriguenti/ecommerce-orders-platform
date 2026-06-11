package com.ecommerce.order.application.order;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.OrderRepositoryPort;

public class GetOrder {

    private final OrderRepositoryPort orderRepository;

    public GetOrder(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Mono<Order> handle(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }
}
