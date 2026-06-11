package com.ecommerce.order.application.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.payment.PaymentStatus;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class HandlePaymentCallbackTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
    private final DomainEventPublisherPort eventPublisher = mock(DomainEventPublisherPort.class);
    private final PaymentResultProcessor processor =
            new PaymentResultProcessor(orderRepository, paymentRepository, eventPublisher);
    private final HandlePaymentCallback useCase =
            new HandlePaymentCallback(paymentRepository, orderRepository, processor);

    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private Order awaitingPaymentOrder() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.addItem(ProductId.of("p1"), Quantity.of(1));
        order.confirm(Map.of(ProductId.of("p1"), Money.of("10.00", "BRL")));
        order.awaitPayment();
        return order;
    }

    private void stubCommonSaves() {
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    @Test
    void approvalMarksOrderPaid() {
        Order order = awaitingPaymentOrder();
        Payment payment = Payment.reconstitute(paymentId, orderId, Money.of("10.00", "BRL"),
                PaymentStatus.PROCESSING, 1);
        when(paymentRepository.findById(paymentId)).thenReturn(Mono.just(payment));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        stubCommonSaves();

        StepVerifier.create(useCase.handle(paymentId, true))
                .assertNext(p -> Assertions.assertThat(p.status()).isEqualTo(PaymentStatus.APPROVED))
                .verifyComplete();

        Assertions.assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void rejectionBelowLimitKeepsOrderRetryable() {
        Order order = awaitingPaymentOrder();
        Payment payment = Payment.reconstitute(paymentId, orderId, Money.of("10.00", "BRL"),
                PaymentStatus.PROCESSING, 1);
        when(paymentRepository.findById(paymentId)).thenReturn(Mono.just(payment));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        stubCommonSaves();

        StepVerifier.create(useCase.handle(paymentId, false))
                .assertNext(p -> Assertions.assertThat(p.status()).isEqualTo(PaymentStatus.REJECTED))
                .verifyComplete();

        Assertions.assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_REJECTED);
    }

    @Test
    void thirdRejectionCancelsOrderAutomatically() {
        Order order = awaitingPaymentOrder();
        Payment payment = Payment.reconstitute(paymentId, orderId, Money.of("10.00", "BRL"),
                PaymentStatus.PROCESSING, 3);
        when(paymentRepository.findById(paymentId)).thenReturn(Mono.just(payment));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        stubCommonSaves();

        StepVerifier.create(useCase.handle(paymentId, false))
                .assertNext(p -> Assertions.assertThat(p.status()).isEqualTo(PaymentStatus.REJECTED))
                .verifyComplete();

        Assertions.assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void reprocessingSameApprovalIsIdempotent() {
        Order order = awaitingPaymentOrder();
        order.markPaid();
        Payment payment = Payment.reconstitute(paymentId, orderId, Money.of("10.00", "BRL"),
                PaymentStatus.APPROVED, 1);
        when(paymentRepository.findById(paymentId)).thenReturn(Mono.just(payment));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.handle(paymentId, true))
                .assertNext(p -> Assertions.assertThat(p.status()).isEqualTo(PaymentStatus.APPROVED))
                .verifyComplete();

        Assertions.assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    }
}
