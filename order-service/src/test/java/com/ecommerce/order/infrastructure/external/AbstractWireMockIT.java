package com.ecommerce.order.infrastructure.external;

import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;

import com.ecommerce.order.support.TestContainers;

/**
 * HTTP-client slice tests run against the shared singleton WireMock in {@link TestContainers}, which
 * loads the very same mappings shipped in {@code wiremock/mappings/} (reused, not duplicated), so the
 * adapters are tested against the real external contracts.
 */
public abstract class AbstractWireMockIT {

    protected static final GenericContainer<?> WIREMOCK = TestContainers.WIREMOCK;

    protected WebClient webClient() {
        return WebClient.builder().baseUrl(TestContainers.wiremockBaseUrl()).build();
    }
}
