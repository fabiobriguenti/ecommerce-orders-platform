package com.ecommerce.order.domain.exception;

public class InvalidQuantityException extends DomainException {

    public InvalidQuantityException(int value) {
        super("Quantity must be greater than zero, but was " + value);
    }
}
