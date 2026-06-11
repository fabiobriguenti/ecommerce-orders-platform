package com.ecommerce.order.application.exception;

import com.ecommerce.order.domain.vo.CustomerId;

public class CustomerBlockedException extends ApplicationException {

    public CustomerBlockedException(CustomerId customerId) {
        super("Customer " + customerId.value() + " is blocked and cannot place orders");
    }
}
