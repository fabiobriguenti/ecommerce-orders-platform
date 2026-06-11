package com.ecommerce.order.infrastructure.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import com.ecommerce.order.application.exception.CustomerBlockedException;
import com.ecommerce.order.application.exception.CustomerNotFoundException;
import com.ecommerce.order.application.exception.DuplicateActiveOrderException;
import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.application.exception.PaymentGatewayUnavailableException;
import com.ecommerce.order.application.exception.PaymentNotFoundException;
import com.ecommerce.order.application.exception.ProductNotAvailableException;
import com.ecommerce.order.application.exception.ProductNotFoundException;
import com.ecommerce.order.domain.exception.EmptyOrderException;
import com.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.ecommerce.order.domain.exception.InvalidPaymentStateException;
import com.ecommerce.order.domain.exception.InvalidQuantityException;
import com.ecommerce.order.domain.exception.ItemNotFoundException;
import com.ecommerce.order.domain.exception.MaxPaymentAttemptsReachedException;
import com.ecommerce.order.domain.exception.MissingProductPriceException;
import com.ecommerce.order.domain.exception.OrderNotModifiableException;

/**
 * Translates domain/application exceptions into RFC 7807 Problem Details. The HTTP status reflects
 * the nature of the violation: {@code 404} for missing resources, {@code 409} for state-machine
 * conflicts, {@code 422} for semantically invalid business operations, {@code 400} for malformed
 * input and {@code 503} when the payment gateway circuit is open (ADR-14).
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    private static final String TYPE_PREFIX = "https://api.ecommerce-orders.com/problems/";

    // ---- 404 Not Found -------------------------------------------------------------------------

    @ExceptionHandler({OrderNotFoundException.class, PaymentNotFoundException.class,
            CustomerNotFoundException.class, ProductNotFoundException.class, ItemNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "not-found");
    }

    // ---- 409 Conflict (state-machine / uniqueness) ---------------------------------------------

    @ExceptionHandler({DuplicateActiveOrderException.class, InvalidOrderStateException.class,
            InvalidPaymentStateException.class, OrderNotModifiableException.class,
            MaxPaymentAttemptsReachedException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflicting state", ex.getMessage(), "conflict");
    }

    // ---- 422 Unprocessable Entity (business validation) ----------------------------------------

    @ExceptionHandler({CustomerBlockedException.class, ProductNotAvailableException.class,
            EmptyOrderException.class, MissingProductPriceException.class})
    public ProblemDetail handleUnprocessable(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation", ex.getMessage(),
                "unprocessable");
    }

    // ---- 400 Bad Request (malformed input) -----------------------------------------------------

    @ExceptionHandler({InvalidQuantityException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "bad-request");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidation(WebExchangeBindException ex) {
        List<Map<String, String>> errors = ex.getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail handleInputException(ServerWebInputException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getReason(), "bad-request");
    }

    // ---- 503 Service Unavailable (payment gateway circuit open) --------------------------------

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ProblemDetail handleGatewayUnavailable(PaymentGatewayUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway unavailable", ex.getMessage(),
                "payment-gateway-unavailable");
    }

    private Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String slug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        return problem;
    }
}
