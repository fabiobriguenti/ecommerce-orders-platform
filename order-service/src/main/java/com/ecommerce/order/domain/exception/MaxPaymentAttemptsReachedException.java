package com.ecommerce.order.domain.exception;

import java.util.UUID;

public class MaxPaymentAttemptsReachedException extends DomainException {

    public MaxPaymentAttemptsReachedException(UUID orderId, int attempts) {
        super("Payment for order " + orderId + " has reached the maximum of " + attempts + " attempts");
    }
}
