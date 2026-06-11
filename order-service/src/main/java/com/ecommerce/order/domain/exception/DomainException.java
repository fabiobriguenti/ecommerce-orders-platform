package com.ecommerce.order.domain.exception;

/**
 * Base type for all business-rule violations in the domain. Infrastructure layers translate
 * these into RFC 7807 responses (Phase 6); the domain itself stays free of HTTP concerns.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
