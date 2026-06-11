package com.ecommerce.order.infrastructure.web.dto;

import java.util.UUID;

import com.ecommerce.order.domain.payment.Payment;

/** View of a payment aggregate, including the number of gateway attempts (max 3). */
public record PaymentResponse(UUID id, UUID orderId, MoneyResponse amount, String status, int attempts) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.orderId(),
                MoneyResponse.from(payment.amount()),
                payment.status().name(),
                payment.attempts());
    }
}
