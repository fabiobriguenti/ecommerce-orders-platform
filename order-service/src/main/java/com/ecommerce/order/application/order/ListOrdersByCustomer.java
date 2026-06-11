package com.ecommerce.order.application.order;

import reactor.core.publisher.Flux;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;

public class ListOrdersByCustomer {

    private final OrderRepositoryPort orderRepository;

    public ListOrdersByCustomer(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Flux<Order> handle(String customerId) {
        return Flux.defer(() -> orderRepository.findByCustomerId(CustomerId.of(customerId)));
    }
}
