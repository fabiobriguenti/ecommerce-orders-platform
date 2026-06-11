package com.ecommerce.order.application.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.ecommerce.order.application.exception.ProductNotAvailableException;
import com.ecommerce.order.application.exception.ProductNotFoundException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.CatalogPort;
import com.ecommerce.order.domain.port.CatalogPort.ProductView;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;

class AddItemTest {

    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final CatalogPort catalogPort = mock(CatalogPort.class);
    private final AddItem useCase = new AddItem(orderRepository, catalogPort);

    private final UUID orderId = UUID.randomUUID();

    @Test
    void failsWhenProductNotFound() {
        when(orderRepository.findById(orderId))
                .thenReturn(Mono.just(Order.create(orderId, CustomerId.of("c1"))));
        when(catalogPort.findById(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.handle(new AddItem.Command(orderId, "p1", 2)))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void failsWhenProductNotAvailable() {
        when(orderRepository.findById(orderId))
                .thenReturn(Mono.just(Order.create(orderId, CustomerId.of("c1"))));
        when(catalogPort.findById(any()))
                .thenReturn(Mono.just(new ProductView(ProductId.of("p1"), false, Money.of("10.00", "BRL"))));

        StepVerifier.create(useCase.handle(new AddItem.Command(orderId, "p1", 2)))
                .expectError(ProductNotAvailableException.class)
                .verify();
    }

    @Test
    void addsAvailableProduct() {
        when(orderRepository.findById(orderId))
                .thenReturn(Mono.just(Order.create(orderId, CustomerId.of("c1"))));
        when(catalogPort.findById(any()))
                .thenReturn(Mono.just(new ProductView(ProductId.of("p1"), true, Money.of("10.00", "BRL"))));
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.handle(new AddItem.Command(orderId, "p1", 2)))
                .assertNext(order -> Assertions.assertThat(order.items()).hasSize(1))
                .verifyComplete();
    }
}
