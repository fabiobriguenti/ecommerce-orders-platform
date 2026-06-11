package com.ecommerce.order.application.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.ecommerce.order.application.exception.CustomerBlockedException;
import com.ecommerce.order.application.exception.CustomerNotFoundException;
import com.ecommerce.order.application.exception.DuplicateActiveOrderException;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.port.CustomerPort;
import com.ecommerce.order.domain.port.CustomerPort.CustomerView;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;

class CreateOrderTest {

    private final CustomerPort customerPort = mock(CustomerPort.class);
    private final OrderRepositoryPort orderRepository = mock(OrderRepositoryPort.class);
    private final CreateOrder useCase = new CreateOrder(customerPort, orderRepository);

    @Test
    void failsWhenCustomerNotFound() {
        when(customerPort.findById(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.handle(new CreateOrder.Command("c1")))
                .expectError(CustomerNotFoundException.class)
                .verify();
    }

    @Test
    void failsWhenCustomerBlocked() {
        when(customerPort.findById(any()))
                .thenReturn(Mono.just(new CustomerView(CustomerId.of("c1"), false)));

        StepVerifier.create(useCase.handle(new CreateOrder.Command("c1")))
                .expectError(CustomerBlockedException.class)
                .verify();
    }

    @Test
    void failsWhenCustomerAlreadyHasActiveOrder() {
        when(customerPort.findById(any()))
                .thenReturn(Mono.just(new CustomerView(CustomerId.of("c1"), true)));
        when(orderRepository.existsActiveByCustomerId(any())).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.handle(new CreateOrder.Command("c1")))
                .expectError(DuplicateActiveOrderException.class)
                .verify();
    }

    @Test
    void createsOrderForActiveCustomer() {
        when(customerPort.findById(any()))
                .thenReturn(Mono.just(new CustomerView(CustomerId.of("c1"), true)));
        when(orderRepository.existsActiveByCustomerId(any())).thenReturn(Mono.just(false));
        when(orderRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.handle(new CreateOrder.Command("c1")))
                .assertNext(order -> {
                    org.assertj.core.api.Assertions.assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
                    org.assertj.core.api.Assertions.assertThat(order.customerId()).isEqualTo(CustomerId.of("c1"));
                })
                .verifyComplete();
    }
}
