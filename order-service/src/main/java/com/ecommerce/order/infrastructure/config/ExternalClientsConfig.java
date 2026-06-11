package com.ecommerce.order.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Single reactive {@link WebClient} pointing at the WireMock-backed external services. Scheduling
 * is enabled here for the outbox poller (ADR-04).
 */
@Configuration
@EnableScheduling
public class ExternalClientsConfig {

    @Bean
    WebClient externalWebClient(WebClient.Builder builder, @Value("${external.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
