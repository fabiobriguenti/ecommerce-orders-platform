package com.ecommerce.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ecommerce.order.domain.exception.EmptyOrderException;
import com.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.ecommerce.order.domain.exception.ItemNotFoundException;
import com.ecommerce.order.domain.exception.MissingProductPriceException;
import com.ecommerce.order.domain.exception.OrderNotModifiableException;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class OrderTest {

    private static final String BRL = "BRL";
    private static final ProductId P1 = ProductId.of("p1");
    private static final ProductId P2 = ProductId.of("p2");

    private Order newOrder() {
        return Order.create(UUID.randomUUID(), CustomerId.of("cust-1"));
    }

    @Test
    void createsInCreatedStatusWithNoItems() {
        Order order = newOrder();
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.items()).isEmpty();
        assertThat(order.total()).isEmpty();
    }

    @Test
    void addingSameProductIncrementsQuantityInsteadOfDuplicating() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(2));
        order.addItem(P1, Quantity.of(3));

        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).quantity()).isEqualTo(Quantity.of(5));
    }

    @Test
    void cannotAddItemsOnceConfirmed() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(1));
        order.confirm(Map.of(P1, Money.of("10.00", BRL)));

        assertThatThrownBy(() -> order.addItem(P2, Quantity.of(1)))
                .isInstanceOf(OrderNotModifiableException.class);
    }

    @Test
    void removingMissingItemFails() {
        Order order = newOrder();
        assertThatThrownBy(() -> order.removeItem(P1)).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void confirmRequiresAtLeastOneItem() {
        Order order = newOrder();
        assertThatThrownBy(() -> order.confirm(Map.of())).isInstanceOf(EmptyOrderException.class);
    }

    @Test
    void confirmComputesTotalFromProvidedPricesAtConfirmationTime() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(2)); // 2 x 10.00
        order.addItem(P2, Quantity.of(1)); // 1 x 5.50

        boolean confirmed = order.confirm(Map.of(
                P1, Money.of("10.00", BRL),
                P2, Money.of("5.50", BRL)));

        assertThat(confirmed).isTrue();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.total()).hasValueSatisfying(
                total -> assertThat(total.amount()).isEqualByComparingTo("25.50"));
        assertThat(order.items()).allMatch(OrderItem::isPriced);
    }

    @Test
    void confirmFailsWhenAPriceIsMissing() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(1));
        assertThatThrownBy(() -> order.confirm(Map.of()))
                .isInstanceOf(MissingProductPriceException.class);
    }

    @Test
    void confirmIsIdempotent() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(1));
        assertThat(order.confirm(Map.of(P1, Money.of("10.00", BRL)))).isTrue();
        assertThat(order.confirm(Map.of(P1, Money.of("99.00", BRL)))).isFalse();
        // total stays frozen from the first confirmation
        assertThat(order.total()).hasValueSatisfying(
                total -> assertThat(total.amount()).isEqualByComparingTo("10.00"));
    }

    @Test
    void confirmRejectedOnCancelledOrder() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(1));
        order.cancel();
        assertThatThrownBy(() -> order.confirm(Map.of(P1, Money.of("10.00", BRL))))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void fullHappyPathToPaid() {
        Order order = confirmedOrder();
        order.awaitPayment();
        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        order.markPaid();
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void rejectionThenRetryPath() {
        Order order = confirmedOrder();
        order.awaitPayment();
        order.markPaymentRejected();
        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_REJECTED);
        order.awaitPayment(); // retry
        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void cannotStartPaymentBeforeConfirmation() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(1));
        assertThatThrownBy(order::awaitPayment).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cannotMarkPaidUnlessAwaitingPayment() {
        Order order = confirmedOrder();
        assertThatThrownBy(order::markPaid).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancelAllowedBeforePaymentApproved() {
        Order order = confirmedOrder();
        order.awaitPayment();
        order.cancel();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotent() {
        Order order = newOrder();
        order.cancel();
        order.cancel();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cannotCancelAfterPaid() {
        Order order = confirmedOrder();
        order.awaitPayment();
        order.markPaid();
        assertThatThrownBy(order::cancel).isInstanceOf(InvalidOrderStateException.class);
    }

    private Order confirmedOrder() {
        Order order = newOrder();
        order.addItem(P1, Quantity.of(1));
        order.confirm(Map.of(P1, Money.of("10.00", BRL)));
        return order;
    }
}
