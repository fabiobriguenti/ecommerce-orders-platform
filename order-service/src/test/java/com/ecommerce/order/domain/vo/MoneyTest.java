package com.ecommerce.order.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesScaleToTwoDecimals() {
        assertThat(Money.of("10", "BRL").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10.005", "BRL").amount()).isEqualByComparingTo("10.01");
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.of("-1", "BRL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsSameCurrency() {
        Money sum = Money.of("10.50", "BRL").add(Money.of("4.50", "BRL"));
        assertThat(sum.amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void rejectsAddingDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("10", "BRL").add(Money.of("10", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }

    @Test
    void multipliesByQuantity() {
        Money result = Money.of("3.33", "BRL").multiply(Quantity.of(3));
        assertThat(result.amount()).isEqualByComparingTo("9.99");
    }

    @Test
    void zeroIsZero() {
        assertThat(Money.zero("BRL").isZero()).isTrue();
    }
}
