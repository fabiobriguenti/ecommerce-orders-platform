package com.ecommerce.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class OrderItemTest {

    @Test
    void startsUnpriced() {
        OrderItem item = OrderItem.of(ProductId.of("p1"), Quantity.of(2));
        assertThat(item.isPriced()).isFalse();
        assertThatThrownBy(item::subtotal).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void incrementKeepsProductAndAddsQuantity() {
        OrderItem item = OrderItem.of(ProductId.of("p1"), Quantity.of(2)).incrementBy(Quantity.of(3));
        assertThat(item.quantity()).isEqualTo(Quantity.of(5));
    }

    @Test
    void subtotalIsUnitPriceTimesQuantity() {
        OrderItem item = OrderItem.of(ProductId.of("p1"), Quantity.of(3)).pricedAt(Money.of("2.50", "BRL"));
        assertThat(item.subtotal().amount()).isEqualByComparingTo("7.50");
    }
}
