package com.ecommerce.order.infrastructure.external;

import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Starts a standalone WireMock container loading the very same mappings shipped in
 * {@code wiremock/mappings/} (reused, not duplicated), so the HTTP-client adapters are tested
 * against the real external contracts.
 */
public abstract class AbstractWireMockIT {

    protected static final GenericContainer<?> WIREMOCK;

    static {
        WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.9.1")
                .withExposedPorts(8080)
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../wiremock/mappings"), "/home/wiremock/mappings")
                .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));
        WIREMOCK.start();
    }

    protected WebClient webClient() {
        String baseUrl = "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
