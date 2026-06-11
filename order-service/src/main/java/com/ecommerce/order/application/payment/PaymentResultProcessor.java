package com.ecommerce.order.application.payment;

import java.time.Instant;

import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.event.DomainEvent;
import com.ecommerce.order.domain.event.OrderCancelled;
import com.ecommerce.order.domain.event.PaymentApproved;
import com.ecommerce.order.domain.event.PaymentRejected;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.payment.PaymentStatus;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;

/**
 * Applies a gateway outcome (approved/rejected) to a payment and its order, shared by
 * {@link StartPayment} (synchronous gateway response) and {@link HandlePaymentCallback}
 * (asynchronous webhook). All operations are idempotent so re-delivery has no side effects:
 * after 3 rejections the order is cancelled automatically.
 */
public class PaymentResultProcessor {

    private final OrderRepositoryPort orderRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final DomainEventPublisherPort eventPublisher;

    public PaymentResultProcessor(OrderRepositoryPort orderRepository,
                                  PaymentRepositoryPort paymentRepository,
                                  DomainEventPublisherPort eventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Payment> apply(Order order, Payment payment, boolean approved) {
        return approved ? applyApproved(order, payment) : applyRejected(order, payment);
    }

    private Mono<Payment> applyApproved(Order order, Payment payment) {
        if (payment.isApproved() && order.status() == OrderStatus.PAID) {
            return Mono.just(payment); // already applied
        }
        payment.approve();
        if (order.status() == OrderStatus.AWAITING_PAYMENT) {
            order.markPaid();
        }
        return persist(order, payment, new PaymentApproved(order.id(), payment.id(), Instant.now()));
    }

    private Mono<Payment> applyRejected(Order order, Payment payment) {
        if (payment.status() == PaymentStatus.REJECTED) {
            return Mono.just(payment); // this attempt was already rejected
        }
        payment.reject();
        DomainEvent event;
        if (payment.hasReachedMaxAttempts()) {
            order.cancel();
            event = new OrderCancelled(order.id(), "MAX_PAYMENT_ATTEMPTS_REACHED", Instant.now());
        } else {
            order.markPaymentRejected();
            event = new PaymentRejected(order.id(), payment.id(), payment.attempts(), Instant.now());
        }
        return persist(order, payment, event);
    }

    private Mono<Payment> persist(Order order, Payment payment, DomainEvent event) {
        return orderRepository.save(order)
                .then(paymentRepository.save(payment))
                .flatMap(saved -> eventPublisher.publish(event).thenReturn(saved));
    }
}
