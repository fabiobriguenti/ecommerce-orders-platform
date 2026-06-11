package com.ecommerce.order.domain.order;

import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

/**
 * Line item of an order. The {@code unitPrice} is {@code null} until the order is confirmed:
 * prices are captured from the catalog at confirmation time (business rule), never at add time.
 */
public record OrderItem(ProductId productId, Quantity quantity, Money unitPrice) {

    public static OrderItem of(ProductId productId, Quantity quantity) {
        return new OrderItem(productId, quantity, null);
    }

    public OrderItem incrementBy(Quantity extra) {
        return new OrderItem(productId, quantity.plus(extra), unitPrice);
    }

    public OrderItem pricedAt(Money price) {
        return new OrderItem(productId, quantity, price);
    }

    public boolean isPriced() {
        return unitPrice != null;
    }

    public Money subtotal() {
        if (unitPrice == null) {
            throw new IllegalStateException("Item " + productId.value() + " has no price yet");
        }
        return unitPrice.multiply(quantity);
    }
}
