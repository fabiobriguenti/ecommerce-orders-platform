package com.ecommerce.order.domain.exception;

import java.util.UUID;

public class EmptyOrderException extends DomainException {

    public EmptyOrderException(UUID orderId) {
        super("Order " + orderId + " must contain at least one item to be confirmed");
    }
}
