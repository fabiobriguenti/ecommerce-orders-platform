package com.ecommerce.order.domain.vo;

import java.util.Objects;

public record CustomerId(String value) {

    public CustomerId {
        Objects.requireNonNull(value, "customerId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
    }

    public static CustomerId of(String value) {
        return new CustomerId(value);
    }
}
