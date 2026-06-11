package com.ecommerce.order.domain.exception;

import com.ecommerce.order.domain.vo.ProductId;

public class ItemNotFoundException extends DomainException {

    public ItemNotFoundException(ProductId productId) {
        super("Item with product " + productId.value() + " was not found in the order");
    }
}
