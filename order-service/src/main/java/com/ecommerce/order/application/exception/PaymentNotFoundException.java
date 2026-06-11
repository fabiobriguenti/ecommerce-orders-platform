package com.ecommerce.order.application.exception;

import java.util.UUID;

public class PaymentNotFoundException extends ApplicationException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment " + paymentId + " was not found");
    }
}
