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

import com.ecommerce.order.domain.event.OrderConfirmed;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.port.CatalogPort;
import com.ecommerce.order.domain.port.CatalogPort.ProductView;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class ConfirmOrderTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final CatalogPort catalogPort = mock(CatalogPort.class);
    private final DomainEventPublisherPort eventPublisher = mock(DomainEventPublisherPort.class);
    private final ConfirmOrder useCase = new ConfirmOrder(orderRepository, catalogPort, eventPublisher);

    private final UUID orderId = UUID.randomUUID();

    @Test
    void confirmsUsingCurrentCatalogPriceAndPublishesEvent() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.addItem(ProductId.of("p1"), Quantity.of(2));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));
        when(catalogPort.findById(any()))
                .thenReturn(Mono.just(new ProductView(ProductId.of("p1"), true, Money.of("10.00", "BRL"))));
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(confirmed -> {
                    Assertions.assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
                    Assertions.assertThat(confirmed.total()).hasValueSatisfying(
                            total -> Assertions.assertThat(total.amount()).isEqualByComparingTo("20.00"));
                })
                .verifyComplete();

        verify(eventPublisher).publish(any(OrderConfirmed.class));
    }

    @Test
    void confirmingAlreadyConfirmedOrderIsNoOp() {
        Order order = Order.create(orderId, CustomerId.of("c1"));
        order.addItem(ProductId.of("p1"), Quantity.of(1));
        order.confirm(Map.of(ProductId.of("p1"), Money.of("10.00", "BRL")));
        when(orderRepository.findById(orderId)).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.handle(orderId))
                .assertNext(o -> Assertions.assertThat(o.status()).isEqualTo(OrderStatus.CONFIRMED))
                .verifyComplete();

        verify(catalogPort, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
