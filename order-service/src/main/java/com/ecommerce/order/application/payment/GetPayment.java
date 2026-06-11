package com.ecommerce.order.application.payment;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.PaymentNotFoundException;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;

public class GetPayment {

    private final PaymentRepositoryPort paymentRepository;

    public GetPayment(PaymentRepositoryPort paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Mono<Payment> handle(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new PaymentNotFoundException(paymentId)));
    }
}
