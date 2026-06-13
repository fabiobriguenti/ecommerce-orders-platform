package com.ecommerce.order.acceptance;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * Security through the real resource-server chain (RSA-signed JWTs minted by the dev token endpoint):
 * anonymous mutations are 401, an authenticated token without the required scope is 403, and a token
 * with the right scope passes. {@code ApiWebTest} covers the same at the slice level; this proves it
 * over real HTTP with genuine token validation.
 */
class SecurityScopeIT extends AbstractIntegrationTest {

    @Test
    void rejectsAnonymousMutationWith401() {
        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "cust-active"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsInsufficientScopeWith403ProblemDetail() {
        String readOnly = bearer("orders:read");

        client.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, readOnly)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "cust-active"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void acceptsTheRequiredScope() {
        String writer = bearer("orders:write");

        postJson(writer, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isCreated();
    }
}
