package com.ecommerce.order.application.payment;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.application.exception.PaymentNotFoundException;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;

/**
 * Idempotent payment webhook. Applies the gateway outcome through the shared
 * {@link PaymentResultProcessor}; processing the same event multiple times has no side effects.
 */
public class HandlePaymentCallback {

    private final PaymentRepositoryPort paymentRepository;
    private final OrderRepositoryPort orderRepository;
    private final PaymentResultProcessor resultProcessor;

    public HandlePaymentCallback(PaymentRepositoryPort paymentRepository,
                                 OrderRepositoryPort orderRepository,
                                 PaymentResultProcessor resultProcessor) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.resultProcessor = resultProcessor;
    }

    public Mono<Payment> handle(UUID paymentId, boolean approved) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new PaymentNotFoundException(paymentId)))
                .flatMap(payment -> orderRepository.findById(payment.orderId())
                        .switchIfEmpty(Mono.error(new OrderNotFoundException(payment.orderId())))
                        .flatMap(order -> resultProcessor.apply(order, payment, approved)));
    }
}
