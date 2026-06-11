package com.ecommerce.order.domain.exception;

import com.ecommerce.order.domain.vo.ProductId;

public class MissingProductPriceException extends DomainException {

    public MissingProductPriceException(ProductId productId) {
        super("No current price was provided for product " + productId.value() + " at confirmation time");
    }
}
