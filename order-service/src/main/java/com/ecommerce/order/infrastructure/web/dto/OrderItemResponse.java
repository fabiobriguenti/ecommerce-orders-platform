package com.ecommerce.order.infrastructure.web.dto;

import com.ecommerce.order.domain.order.OrderItem;

/**
 * A line item. {@code unitPrice} and {@code subtotal} are {@code null} until the order is confirmed:
 * prices are captured from the catalog at confirmation time, never at add time.
 */
public record OrderItemResponse(String productId, int quantity, MoneyResponse unitPrice,
                                MoneyResponse subtotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.productId().value(),
                item.quantity().value(),
                MoneyResponse.from(item.unitPrice()),
                item.isPriced() ? MoneyResponse.from(item.subtotal()) : null);
    }
}
