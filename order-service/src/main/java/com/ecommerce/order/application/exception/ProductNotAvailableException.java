package com.ecommerce.order.application.exception;

import com.ecommerce.order.domain.vo.ProductId;

public class ProductNotAvailableException extends ApplicationException {

    public ProductNotAvailableException(ProductId productId) {
        super("Product " + productId.value() + " is not available");
    }
}
