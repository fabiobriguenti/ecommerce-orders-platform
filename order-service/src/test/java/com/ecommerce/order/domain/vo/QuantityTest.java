package com.ecommerce.order.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ecommerce.order.domain.exception.InvalidQuantityException;

class QuantityTest {

    @Test
    void rejectsZeroOrNegative() {
        assertThatThrownBy(() -> Quantity.of(0)).isInstanceOf(InvalidQuantityException.class);
        assertThatThrownBy(() -> Quantity.of(-5)).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void sumsQuantities() {
        assertThat(Quantity.of(2).plus(Quantity.of(3))).isEqualTo(Quantity.of(5));
    }
}
