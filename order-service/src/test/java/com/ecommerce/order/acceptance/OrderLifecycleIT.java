package com.ecommerce.order.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * End-to-end happy path through the real stack (HTTP → security → use cases → R2DBC/Postgres →
 * WireMock-backed catalog & payment gateway): create → add item → confirm → pay. Asserts the two
 * rules that only show up once everything is wired: the total freezes from the catalog price at
 * confirmation, and adding the same product increments the existing line.
 */
class OrderLifecycleIT extends AbstractIntegrationTest {

    @Test
    void confirmsAndPaysAnOrder_freezingTheCatalogPriceAtConfirmation() {
        String auth = bearer("orders:read", "orders:write", "payments:read", "payments:write");

        String orderId = newConfirmedOrder(auth, "cust-active", "prod-available", 2);

        // Confirmed: total frozen at 2 × 10.00 (catalog price) = 20.00 BRL.
        client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED")
                .jsonPath("$.total.currency").isEqualTo("BRL")
                .jsonPath("$.total.amount").value(amount ->
                        assertThat(new BigDecimal(amount.toString())).isEqualByComparingTo("20.00"));

        // The gateway approves by default → payment APPROVED, order PAID.
        postJson(auth, "/api/v1/payments", Map.of("orderId", orderId))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.status").isEqualTo("APPROVED");

        client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PAID");
    }

    @Test
    void addingTheSameProductIncrementsTheLineInsteadOfDuplicating() {
        String auth = bearer("orders:read", "orders:write");

        String orderId = createOrder(auth);

        postJson(auth, "/api/v1/orders/" + orderId + "/items",
                Map.of("productId", "prod-available", "quantity", 1)).expectStatus().isOk();
        postJson(auth, "/api/v1/orders/" + orderId + "/items",
                Map.of("productId", "prod-available", "quantity", 1)).expectStatus().isOk();

        client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].quantity").isEqualTo(2);
    }

    private String createOrder(String auth) {
        return postJson(auth, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isCreated()
                .expectBody(com.fasterxml.jackson.databind.JsonNode.class)
                .returnResult().getResponseBody().get("id").asText();
    }
}
