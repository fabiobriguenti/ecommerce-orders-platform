package com.ecommerce.order.application.payment;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.payment.PaymentStatus;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.port.PaymentGatewayPort;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;
import com.ecommerce.order.domain.vo.Money;

/**
 * Starts (or retries) the payment of a confirmed order and requests the charge from the gateway.
 * Idempotent: if a payment is already PROCESSING or APPROVED for the order, it is returned as-is.
 * After a rejection (order in PAYMENT_REJECTED, attempts &lt; 3) a new attempt is made.
 */
public class StartPayment {

    private final OrderRepositoryPort orderRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort gateway;
    private final PaymentResultProcessor resultProcessor;

    public StartPayment(OrderRepositoryPort orderRepository, PaymentRepositoryPort paymentRepository,
                        PaymentGatewayPort gateway, PaymentResultProcessor resultProcessor) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.resultProcessor = resultProcessor;
    }

    public Mono<Payment> handle(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> paymentRepository.findByOrderId(orderId)
                        .flatMap(existing -> resumeOrReturn(order, existing))
                        .switchIfEmpty(Mono.defer(() -> initiate(order))));
    }

    private Mono<Payment> initiate(Order order) {
        if (order.status() != OrderStatus.CONFIRMED) {
            return Mono.error(new InvalidOrderStateException("startPayment", order.status()));
        }
        Money amount = order.total()
                .orElseThrow(() -> new IllegalStateException("confirmed order " + order.id() + " has no total"));
        Payment payment = Payment.initiate(UUID.randomUUID(), order.id(), amount);
        order.awaitPayment();
        return orderRepository.save(order)
                .then(paymentRepository.save(payment))
                .flatMap(saved -> charge(order, saved));
    }

    private Mono<Payment> resumeOrReturn(Order order, Payment payment) {
        boolean retryable = payment.status() == PaymentStatus.REJECTED
                && order.status() == OrderStatus.PAYMENT_REJECTED
                && !payment.hasReachedMaxAttempts();
        if (!retryable) {
            return Mono.just(payment); // idempotent: processing/approved or no retry possible
        }
        payment.retry();
        order.awaitPayment();
        return orderRepository.save(order)
                .then(paymentRepository.save(payment))
                .flatMap(saved -> charge(order, saved));
    }

    private Mono<Payment> charge(Order order, Payment payment) {
        return gateway.charge(new PaymentGatewayPort.ChargeCommand(order.id(), payment.id(), payment.amount()))
                .flatMap(result -> resultProcessor.apply(order, payment, result.approved()));
    }
}
