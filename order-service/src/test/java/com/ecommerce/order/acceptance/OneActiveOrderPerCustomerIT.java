package com.ecommerce.order.acceptance;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * Business rule: at most one active order per customer. A second create while the first is still
 * active is rejected with 409 (RFC 7807); once the first order reaches a terminal state a new one is
 * allowed again.
 */
class OneActiveOrderPerCustomerIT extends AbstractIntegrationTest {

    @Test
    void rejectsASecondActiveOrderForTheSameCustomer() {
        String auth = bearer("orders:read", "orders:write");

        postJson(auth, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isCreated();

        postJson(auth, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.title").isEqualTo("Conflicting state");
    }

    @Test
    void allowsANewOrderOnceThePreviousOneIsCancelled() {
        String auth = bearer("orders:read", "orders:write");

        String firstId = postJson(auth, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isCreated()
                .expectBody(com.fasterxml.jackson.databind.JsonNode.class)
                .returnResult().getResponseBody().get("id").asText();

        client.delete().uri("/api/v1/orders/{id}", firstId)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isOk();

        // Previous order CANCELLED (terminal) → a fresh active order is permitted.
        postJson(auth, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isCreated();
    }
}
