package com.ecommerce.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class OrderRepositoryAdapterIT extends AbstractPostgresIT {

    private OrderRepositoryAdapter adapter() {
        return new OrderRepositoryAdapter(template);
    }

    private Order confirmedOrder(String customer) {
        Order order = Order.create(UUID.randomUUID(), CustomerId.of(customer));
        order.addItem(ProductId.of("p1"), Quantity.of(2));
        order.addItem(ProductId.of("p2"), Quantity.of(1));
        order.confirm(Map.of(
                ProductId.of("p1"), Money.of("10.00", "BRL"),
                ProductId.of("p2"), Money.of("5.50", "BRL")));
        return order;
    }

    @Test
    void savesAndReloadsOrderWithItemsAndTotal() {
        OrderRepositoryAdapter adapter = adapter();
        Order order = confirmedOrder("cust-1");

        StepVerifier.create(adapter.save(order)
                        .then(adapter.findById(order.id())))
                .assertNext(loaded -> {
                    assertThat(loaded.status()).isEqualTo(OrderStatus.CONFIRMED);
                    assertThat(loaded.items()).hasSize(2);
                    assertThat(loaded.total()).hasValueSatisfying(
                            total -> assertThat(total.amount()).isEqualByComparingTo("25.50"));
                    assertThat(loaded.version()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void sequentialSavesIncrementVersion() {
        OrderRepositoryAdapter adapter = adapter();
        Order order = Order.create(UUID.randomUUID(), CustomerId.of("cust-2"));

        StepVerifier.create(adapter.save(order))
                .assertNext(saved -> assertThat(saved.version()).isZero())
                .verifyComplete();

        order.addItem(ProductId.of("p1"), Quantity.of(1));
        StepVerifier.create(adapter.save(order))
                .assertNext(saved -> assertThat(saved.version()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void detectsConcurrentModificationViaOptimisticLocking() {
        OrderRepositoryAdapter adapter = adapter();
        Order order = Order.create(UUID.randomUUID(), CustomerId.of("cust-3"));
        adapter.save(order).block();

        Order a = adapter.findById(order.id()).block();
        Order b = adapter.findById(order.id()).block();

        a.addItem(ProductId.of("p1"), Quantity.of(1));
        adapter.save(a).block();

        b.addItem(ProductId.of("p2"), Quantity.of(1));
        StepVerifier.create(adapter.save(b))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
    }

    @Test
    void existsActiveReflectsTerminalStatus() {
        OrderRepositoryAdapter adapter = adapter();
        Order order = Order.create(UUID.randomUUID(), CustomerId.of("cust-4"));
        adapter.save(order).block();

        StepVerifier.create(adapter.existsActiveByCustomerId(CustomerId.of("cust-4")))
                .expectNext(true)
                .verifyComplete();

        order.cancel();
        adapter.save(order).block();

        StepVerifier.create(adapter.existsActiveByCustomerId(CustomerId.of("cust-4")))
                .expectNext(false)
                .verifyComplete();
    }
}
