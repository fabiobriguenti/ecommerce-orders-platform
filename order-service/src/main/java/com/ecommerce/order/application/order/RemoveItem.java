package com.ecommerce.order.application.order;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.ProductId;

public class RemoveItem {

    private final OrderRepositoryPort orderRepository;

    public RemoveItem(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Mono<Order> handle(UUID orderId, String productId) {
        return Mono.defer(() -> {
            ProductId pid = ProductId.of(productId);
            return orderRepository.findById(orderId)
                    .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                    .flatMap(order -> {
                        order.removeItem(pid);
                        return orderRepository.save(order);
                    });
        });
    }
}
