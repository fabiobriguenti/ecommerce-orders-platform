package com.ecommerce.order.domain.exception;

import com.ecommerce.order.domain.order.OrderStatus;

public class InvalidOrderStateException extends DomainException {

    public InvalidOrderStateException(String operation, OrderStatus status) {
        super("Operation '" + operation + "' is not allowed for an order in status " + status);
    }
}
