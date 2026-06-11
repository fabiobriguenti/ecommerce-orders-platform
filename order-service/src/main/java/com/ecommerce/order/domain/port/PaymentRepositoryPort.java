package com.ecommerce.order.domain.port;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.payment.Payment;

public interface PaymentRepositoryPort {

    Mono<Payment> save(Payment payment);

    Mono<Payment> findById(UUID id);

    Mono<Payment> findByOrderId(UUID orderId);
}
