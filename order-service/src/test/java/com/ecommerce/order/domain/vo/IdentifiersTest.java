package com.ecommerce.order.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void customerIdRejectsBlank() {
        assertThatThrownBy(() -> CustomerId.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(CustomerId.of("cust-1").value()).isEqualTo("cust-1");
    }

    @Test
    void productIdRejectsBlank() {
        assertThatThrownBy(() -> ProductId.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ProductId.of("prod-1").value()).isEqualTo("prod-1");
    }
}
