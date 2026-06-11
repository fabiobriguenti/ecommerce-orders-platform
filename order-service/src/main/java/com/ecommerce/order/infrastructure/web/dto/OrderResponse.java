package com.ecommerce.order.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

import com.ecommerce.order.domain.order.Order;

/** Full view of an order aggregate. {@code total} is present only once the order is confirmed. */
public record OrderResponse(UUID id, String customerId, String status,
                            List<OrderItemResponse> items, MoneyResponse total, Long version) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId().value(),
                order.status().name(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                order.total().map(MoneyResponse::from).orElse(null),
                order.version());
    }
}
