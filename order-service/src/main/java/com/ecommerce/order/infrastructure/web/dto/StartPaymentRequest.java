package com.ecommerce.order.infrastructure.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/payments}: starts the payment of a confirmed order. */
public record StartPaymentRequest(@NotNull UUID orderId) {
}
