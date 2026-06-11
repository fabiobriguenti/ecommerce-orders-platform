package com.ecommerce.order.infrastructure.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import com.ecommerce.order.application.payment.GetPayment;
import com.ecommerce.order.application.payment.HandlePaymentCallback;
import com.ecommerce.order.application.payment.StartPayment;
import com.ecommerce.order.infrastructure.web.dto.PaymentCallbackRequest;
import com.ecommerce.order.infrastructure.web.dto.PaymentResponse;
import com.ecommerce.order.infrastructure.web.dto.StartPaymentRequest;

/**
 * Payments REST API. Starting a payment and the gateway callback are idempotent at the domain
 * level (re-sends have no side effects) and additionally protected by the {@code Idempotency-Key}
 * header ({@link IdempotencyKeyFilter}). Mutating handlers run in a reactive transaction so the
 * payment/order change and its outbox event commit atomically (ADR-04).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final StartPayment startPayment;
    private final GetPayment getPayment;
    private final HandlePaymentCallback handlePaymentCallback;
    private final TransactionalOperator tx;

    public PaymentController(StartPayment startPayment, GetPayment getPayment,
                             HandlePaymentCallback handlePaymentCallback, TransactionalOperator tx) {
        this.startPayment = startPayment;
        this.getPayment = getPayment;
        this.handlePaymentCallback = handlePaymentCallback;
        this.tx = tx;
    }

    @PostMapping
    public Mono<PaymentResponse> start(@Valid @RequestBody StartPaymentRequest request) {
        return startPayment.handle(request.orderId())
                .as(tx::transactional)
                .map(PaymentResponse::from);
    }

    @GetMapping("/{paymentId}")
    public Mono<PaymentResponse> getById(@PathVariable UUID paymentId) {
        return getPayment.handle(paymentId).map(PaymentResponse::from);
    }

    @PostMapping("/{paymentId}/callback")
    public Mono<PaymentResponse> callback(@PathVariable UUID paymentId,
                                          @Valid @RequestBody PaymentCallbackRequest request) {
        return handlePaymentCallback.handle(paymentId, request.approved())
                .as(tx::transactional)
                .map(PaymentResponse::from);
    }
}
