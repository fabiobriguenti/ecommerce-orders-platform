package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Phase 1 placeholder test: keeps the test phase meaningful without requiring Docker/Postgres.
 * It is replaced by domain unit tests (Phase 2) and Testcontainers integration tests (Phase 9).
 */
class SmokeTest {

    @Test
    void contextSanity() {
        assertThat(OrderServiceApplication.class).isNotNull();
    }
}
