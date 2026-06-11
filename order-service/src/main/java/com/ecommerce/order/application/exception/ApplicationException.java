package com.ecommerce.order.application.exception;

/**
 * Base type for application-level rule violations that arise from orchestrating the domain with
 * external state (customer/product existence, lookups). Infrastructure maps these to RFC 7807.
 */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }
}
