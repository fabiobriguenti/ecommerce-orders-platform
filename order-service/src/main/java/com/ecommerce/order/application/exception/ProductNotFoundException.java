package com.ecommerce.order.application.exception;

import com.ecommerce.order.domain.vo.ProductId;

public class ProductNotFoundException extends ApplicationException {

    public ProductNotFoundException(ProductId productId) {
        super("Product " + productId.value() + " was not found");
    }
}
