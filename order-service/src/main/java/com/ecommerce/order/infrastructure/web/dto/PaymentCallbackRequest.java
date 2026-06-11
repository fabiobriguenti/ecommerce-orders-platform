package com.ecommerce.order.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/v1/payments/{paymentId}/callback} (gateway webhook). Reprocessing the
 * same outcome is idempotent and has no side effects.
 */
public record PaymentCallbackRequest(@NotNull Outcome status) {

    public boolean approved() {
        return status == Outcome.APPROVED;
    }

    public enum Outcome {
        APPROVED,
        REJECTED
    }
}
