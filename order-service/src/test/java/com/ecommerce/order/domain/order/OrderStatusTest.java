package com.ecommerce.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void allowsValidTransitions() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.AWAITING_PAYMENT)).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.canTransitionTo(OrderStatus.PAYMENT_REJECTED)).isTrue();
        assertThat(OrderStatus.PAYMENT_REJECTED.canTransitionTo(OrderStatus.AWAITING_PAYMENT)).isTrue();
    }

    @Test
    void rejectsInvalidTransitions() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void onlyCreatedAcceptsItems() {
        assertThat(OrderStatus.CREATED.acceptsItems()).isTrue();
        assertThat(OrderStatus.CONFIRMED.acceptsItems()).isFalse();
    }

    @Test
    void paidAndCancelledAreTerminal() {
        assertThat(OrderStatus.PAID.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isTerminal()).isFalse();
    }
}
