package com.ecommerce.order.acceptance;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * Resilience rule: an unstable payment gateway (HTTP 503) must not take the platform down. The order
 * total is 99.99, which the gateway mock answers with 503. After a couple of failures the breaker
 * (configured tight in {@code application-test.yml}) opens and StartPayment fails fast with a clean
 * 503 Problem Detail rather than hanging or cascading — and the rest of the API stays responsive.
 */
class CircuitBreakerIT extends AbstractIntegrationTest {

    @Test
    void opensTheBreakerAndFailsFastWhileTheApiStaysUp() {
        String auth = bearer("orders:read", "orders:write", "payments:write");
        String orderId = newConfirmedOrder(auth, "cust-active", "prod-unstable", 1);

        boolean sawCircuitOpen = false;
        for (int attempt = 0; attempt < 6 && !sawCircuitOpen; attempt++) {
            int status = postJson(auth, "/api/v1/payments", Map.of("orderId", orderId))
                    .returnResult(Void.class).getStatus().value();
            if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                sawCircuitOpen = true;
            }
        }

        org.assertj.core.api.Assertions.assertThat(sawCircuitOpen)
                .as("circuit breaker should open and surface a 503 within a few attempts")
                .isTrue();

        // The breaker is now OPEN: the next call short-circuits to the documented Problem Detail.
        postJson(auth, "/api/v1/payments", Map.of("orderId", orderId))
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Payment gateway unavailable")
                .jsonPath("$.type").value(t ->
                        org.assertj.core.api.Assertions.assertThat(t.toString()).endsWith("payment-gateway-unavailable"));

        // Platform is unharmed: an unrelated read still works, and the order was never corrupted
        // (each failed StartPayment rolled back, so it is still CONFIRMED).
        client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CONFIRMED");
    }
}
