package com.ecommerce.order.application.order;

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
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class CancelOrderTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final DomainEventPublisherPort eventPublisher = mock(DomainEventPublisherPort.class);
    private final CancelOrder useCase = new CancelOrder(orderRepository, eventPublisher);

    private final UUID orderId = UUID.randomUUID();

    @Test
    void cancelsAndPublishesEvent() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(o -> Assertions.assertThat(o.status()).isEqualTo(OrderStatus.CANCELLED))
                .verifyComplete();

        verify(eventPublisher).publish(any());
    }

    @Test
    void cancellingAlreadyCancelledOrderDoesNotRepublish() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.cancel();
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(o -> Assertions.assertThat(o.status()).isEqualTo(OrderStatus.CANCELLED))
                .verifyComplete();

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void cannotCancelPaidOrder() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.addItem(ProductId.of("p1"), Quantity.of(1));
        order.confirm(Map.of(ProductId.of("p1"), Money.of("10.00", "BRL")));
        order.awaitPayment();
        order.markPaid();
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.handle(orderId))
                .expectError(InvalidOrderStateException.class)
                .verify();
    }
}
