package com.ecommerce.order.application.exception;

import java.util.UUID;

public class OrderNotFoundException extends ApplicationException {

    public OrderNotFoundException(UUID orderId) {
        super("Order " + orderId + " was not found");
    }
}
