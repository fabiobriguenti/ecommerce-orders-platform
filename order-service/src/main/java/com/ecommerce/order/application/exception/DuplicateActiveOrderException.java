package com.ecommerce.order.application.exception;

import com.ecommerce.order.domain.vo.CustomerId;

public class DuplicateActiveOrderException extends ApplicationException {

    public DuplicateActiveOrderException(CustomerId customerId) {
        super("Customer " + customerId.value() + " already has an active order");
    }
}
