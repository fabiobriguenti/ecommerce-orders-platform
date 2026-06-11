package com.ecommerce.order.domain.vo;

import java.util.Objects;

public record ProductId(String value) {

    public ProductId {
        Objects.requireNonNull(value, "productId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
    }

    public static ProductId of(String value) {
        return new ProductId(value);
    }
}
