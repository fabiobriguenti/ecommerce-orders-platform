package com.ecommerce.order.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /api/v1/orders/{orderId}/items}. */
public record AddItemRequest(@NotBlank String productId, @Positive int quantity) {
}
