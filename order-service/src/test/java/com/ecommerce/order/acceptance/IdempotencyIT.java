package com.ecommerce.order.acceptance;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * Exercises {@code IdempotencyKeyFilter} through real HTTP. The store itself is unit-covered by
 * {@code IdempotencyStoreIT}; this verifies the filter's end-to-end contract: same key + same payload
 * replays the cached response (and creates nothing new), while reusing a key with a different payload
 * is rejected with 409 (RFC 7807).
 */
class IdempotencyIT extends AbstractIntegrationTest {

    @Test
    void replaysTheCachedResponseForTheSameKeyAndPayload() {
        String auth = bearer("orders:read", "orders:write");
        String key = UUID.randomUUID().toString();

        String firstId = createWithKey(auth, key, "cust-active").get("id").asText();
        // A second create for the same active customer would normally be a 409 duplicate; the
        // idempotent replay short-circuits to the cached 201 with the same order id instead.
        String secondId = createWithKey(auth, key, "cust-active").get("id").asText();

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);

        // And only one order actually exists for the customer.
        client.get().uri("/api/v1/orders?customerId=cust-active")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void rejectsTheSameKeyReusedWithADifferentPayload() {
        String auth = bearer("orders:write");
        String key = UUID.randomUUID().toString();

        createWithKey(auth, key, "cust-active");

        client.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "cust-active-other"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.title").isEqualTo("Idempotency conflict");
    }

    private JsonNode createWithKey(String auth, String key, String customerId) {
        return client.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", customerId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
    }
}
