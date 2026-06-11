package com.ecommerce.order.application.exception;

import com.ecommerce.order.domain.vo.CustomerId;

public class CustomerNotFoundException extends ApplicationException {

    public CustomerNotFoundException(CustomerId customerId) {
        super("Customer " + customerId.value() + " was not found");
    }
}
