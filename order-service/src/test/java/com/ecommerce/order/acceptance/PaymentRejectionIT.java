package com.ecommerce.order.acceptance;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * Drives the retryable-payment rule end-to-end. The order total is 77.77, which the payment gateway
 * mock rejects; each StartPayment is a fresh attempt, and after the 3rd rejection the order is
 * cancelled automatically. Also checks the gateway callback is idempotent on the terminal payment.
 */
class PaymentRejectionIT extends AbstractIntegrationTest {

    @Test
    void cancelsTheOrderAutomaticallyAfterThreeRejections() {
        String auth = bearer("orders:read", "orders:write", "payments:read", "payments:write");
        String orderId = newConfirmedOrder(auth, "cust-active", "prod-rejecting", 1);

        startPayment(auth, orderId).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("REJECTED").jsonPath("$.attempts").isEqualTo(1);

        startPayment(auth, orderId).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("REJECTED").jsonPath("$.attempts").isEqualTo(2);

        JsonNode third = startPayment(auth, orderId).expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(third.get("status").asText()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(third.get("attempts").asInt()).isEqualTo(3);
        String paymentId = third.get("id").asText();

        // Order auto-cancelled on the 3rd rejection.
        client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED");

        // Replaying the rejection callback on the now-terminal payment is a harmless no-op.
        postJson(auth, "/api/v1/payments/" + paymentId + "/callback", Map.of("status", "REJECTED"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.attempts").isEqualTo(3);

        // Starting payment again on a cancelled order is idempotent: still cancelled, still 3 attempts.
        startPayment(auth, orderId).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("REJECTED").jsonPath("$.attempts").isEqualTo(3);
        client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec startPayment(
            String auth, String orderId) {
        return postJson(auth, "/api/v1/payments", Map.of("orderId", orderId));
    }
}
