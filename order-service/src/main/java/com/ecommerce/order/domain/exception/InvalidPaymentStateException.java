package com.ecommerce.order.domain.exception;

import com.ecommerce.order.domain.payment.PaymentStatus;

public class InvalidPaymentStateException extends DomainException {

    public InvalidPaymentStateException(String operation, PaymentStatus status) {
        super("Operation '" + operation + "' is not allowed for a payment in status " + status);
    }
}
