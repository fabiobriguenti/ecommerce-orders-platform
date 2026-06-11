package com.ecommerce.order.application.exception;

import java.util.UUID;

/**
 * Raised when the payment gateway is unavailable and the circuit breaker is open (ADR-14):
 * the platform fails fast for payments instead of cascading the gateway outage.
 */
public class PaymentGatewayUnavailableException extends ApplicationException {

    public PaymentGatewayUnavailableException(UUID orderId) {
        super("Payment gateway is currently unavailable for order " + orderId + ", please retry later");
    }
}
