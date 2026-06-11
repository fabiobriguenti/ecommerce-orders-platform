package com.ecommerce.order.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/orders}. */
public record CreateOrderRequest(@NotBlank String customerId) {
}
