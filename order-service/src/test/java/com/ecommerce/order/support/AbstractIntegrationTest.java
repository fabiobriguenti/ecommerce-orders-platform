package com.ecommerce.order.support;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base class for the full-stack acceptance tests. Boots the whole application on a random port
 * (real web/security/persistence/HTTP-client wiring) against the shared {@link TestContainers}
 * Postgres and WireMock. Because every subclass registers the very same dynamic properties (they all
 * point at the same singleton containers), Spring's context cache hands them one shared
 * {@code ApplicationContext} — the containers and context are started once for the entire suite.
 *
 * <p>State is isolated per test: the tables are truncated and the payment-gateway circuit breaker is
 * reset in {@link #resetState()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected WebTestClient client;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + TestContainers.POSTGRES.getHost() + ":"
                + TestContainers.POSTGRES.getFirstMappedPort() + "/orders");
        registry.add("spring.r2dbc.username", TestContainers.POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", TestContainers.POSTGRES::getPassword);
        registry.add("spring.flyway.url", TestContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", TestContainers.POSTGRES::getUsername);
        registry.add("spring.flyway.password", TestContainers.POSTGRES::getPassword);
        registry.add("external.base-url", TestContainers::wiremockBaseUrl);
    }

    @BeforeEach
    void resetState() {
        databaseClient.sql("TRUNCATE orders, order_items, payments, outbox, idempotency_keys CASCADE")
                .fetch().rowsUpdated().block();
        if (circuitBreakerRegistry != null) {
            // The breaker bean is a shared singleton across the cached context; reset it so a test
            // that trips it (CircuitBreakerIT) cannot leak an OPEN state into the next test.
            circuitBreakerRegistry.circuitBreaker("paymentGateway").reset();
        }
    }

    /** Mints a real RSA-signed JWT with the given scopes via the dev-only token endpoint. */
    protected String bearer(String... scopes) {
        Token token = client.post()
                .uri(uri -> uri.path("/api/v1/auth/token").queryParam("scope", (Object[]) scopes).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Token.class)
                .returnResult()
                .getResponseBody();
        return "Bearer " + token.accessToken();
    }

    /** create → add one product line → confirm, returning the order id. Total freezes at confirm. */
    protected String newConfirmedOrder(String auth, String customerId, String productId, int quantity) {
        String orderId = postJson(auth, "/api/v1/orders", Map.of("customerId", customerId))
                .expectStatus().isCreated()
                .expectBody(JsonNode.class).returnResult().getResponseBody().get("id").asText();
        postJson(auth, "/api/v1/orders/" + orderId + "/items",
                Map.of("productId", productId, "quantity", quantity))
                .expectStatus().isOk();
        client.post().uri("/api/v1/orders/{id}/confirm", orderId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isOk();
        return orderId;
    }

    protected WebTestClient.ResponseSpec postJson(String auth, String uri, Object body) {
        return client.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Token(String accessToken) {
    }
}
