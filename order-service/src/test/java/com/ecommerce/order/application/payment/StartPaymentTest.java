package com.ecommerce.order.application.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.payment.PaymentStatus;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.port.PaymentGatewayPort;
import com.ecommerce.order.domain.port.PaymentGatewayPort.ChargeResult;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class StartPaymentTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final PaymentRepositoryPort paymentRepository = mock(PaymentRepositoryPort.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final DomainEventPublisherPort eventPublisher = mock(DomainEventPublisherPort.class);
    private final PaymentResultProcessor processor =
            new PaymentResultProcessor(orderRepository, paymentRepository, eventPublisher);
    private final StartPayment useCase =
            new StartPayment(orderRepository, paymentRepository, gateway, processor);

    private final UUID orderId = UUID.randomUUID();

    private Order confirmedOrder() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.addItem(ProductId.of("p1"), Quantity.of(1));
        order.confirm(Map.of(ProductId.of("p1"), Money.of("10.00", "BRL")));
        return order;
    }

    @Test
    void initiatesPaymentAndApprovesOrderWhenGatewayApproves() {
        Order order = confirmedOrder();
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Mono.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(gateway.charge(any())).thenReturn(Mono.just(new ChargeResult(true, "ref-1")));

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(payment -> Assertions.assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED))
                .verifyComplete();

        Assertions.assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void rejectionKeepsOrderRetryable() {
        Order order = confirmedOrder();
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Mono.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(gateway.charge(any())).thenReturn(Mono.just(new ChargeResult(false, "ref-1")));

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(payment -> Assertions.assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED))
                .verifyComplete();

        Assertions.assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_REJECTED);
    }

    @Test
    void cannotStartPaymentForUnconfirmedOrder() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.addItem(ProductId.of("p1"), Quantity.of(1));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.handle(orderId))
                .expectError(InvalidOrderStateException.class)
                .verify();

        verify(gateway, never()).charge(any());
    }

    @Test
    void existingProcessingPaymentIsReturnedWithoutChargingAgain() {
        Order order = confirmedOrder();
        order.awaitPayment();
        Payment existing = Payment.initiate(UUID.randomUUID(), orderId, Money.of("10.00", "BRL"));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(payment -> Assertions.assertThat(payment).isSameAs(existing))
                .verifyComplete();

        verify(gateway, never()).charge(any());
    }
}
