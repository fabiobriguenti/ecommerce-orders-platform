package com.ecommerce.order.domain.vo;

import com.ecommerce.order.domain.exception.InvalidQuantityException;

/**
 * A strictly positive item quantity (business rule: quantity must be greater than zero).
 */
public record Quantity(int value) {

    public Quantity {
        if (value <= 0) {
            throw new InvalidQuantityException(value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public Quantity plus(Quantity other) {
        return new Quantity(this.value + other.value);
    }
}
