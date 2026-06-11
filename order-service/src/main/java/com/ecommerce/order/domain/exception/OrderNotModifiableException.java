package com.ecommerce.order.domain.exception;

import java.util.UUID;

import com.ecommerce.order.domain.order.OrderStatus;

public class OrderNotModifiableException extends DomainException {

    public OrderNotModifiableException(UUID orderId, OrderStatus status) {
        super("Order " + orderId + " cannot have its items modified while in status " + status);
    }
}
