package com.ecommerce.order.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import com.ecommerce.order.support.AbstractIntegrationTest;

/**
 * Concurrency rule (ADR-05): concurrent requests on the same order must be handled correctly. Fires a
 * burst of parallel "add item" requests for the same product against a fresh order. Optimistic
 * locking must keep the data consistent — the final quantity equals exactly the number of requests
 * that succeeded (no lost updates) — and every losing request gets a clean 409, never a 500.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    private static final int BURST = 8;

    @LocalServerPort
    int port;

    @Autowired
    WebClient.Builder webClientBuilder;

    @Test
    void concurrentAddItemKeepsTheOrderConsistentAndConflictsReturn409() {
        String auth = bearer("orders:read", "orders:write");
        String orderId = createOrder(auth);

        WebClient http = webClientBuilder.baseUrl("http://localhost:" + port).build();

        List<Integer> statuses = Flux.range(0, BURST)
                .flatMap(i -> http.post().uri("/api/v1/orders/{id}/items", orderId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(Map.of("productId", "prod-available", "quantity", 1)))
                        .exchangeToMono(r -> reactor.core.publisher.Mono.just(r.statusCode().value())))
                .collectList()
                .block();

        // No request blew up: every outcome is either success (200) or a conflict (409).
        assertThat(statuses).isNotNull().allSatisfy(s -> assertThat(s).isIn(200, 409));

        long successes = statuses.stream().filter(s -> s == 200).count();
        assertThat(successes).isGreaterThanOrEqualTo(1);

        // Integrity: the persisted quantity equals the number of successful increments — no lost
        // updates despite the race.
        JsonNode order = client.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(order.get("items")).hasSize(1);
        assertThat(order.get("items").get(0).get("quantity").asInt()).isEqualTo((int) successes);
    }

    private String createOrder(String auth) {
        return postJson(auth, "/api/v1/orders", Map.of("customerId", "cust-active"))
                .expectStatus().isCreated()
                .expectBody(JsonNode.class).returnResult().getResponseBody().get("id").asText();
    }
}
