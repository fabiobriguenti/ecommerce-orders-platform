package com.ecommerce.order.infrastructure.config;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Single reactive {@link WebClient} pointing at the WireMock-backed external services, backed by an
 * explicitly sized Reactor Netty connection pool (tunable via {@code external.pool.*}) instead of the
 * library defaults. Scheduling is enabled here for the outbox poller (ADR-04).
 *
 * <p>No global response timeout is set on purpose: the payment gateway's latency budget is owned by
 * the Resilience4j TimeLimiter (ADR-14); a second timeout here would fight it.
 */
@Configuration
@EnableScheduling
public class ExternalClientsConfig {

    @Bean
    WebClient externalWebClient(
            WebClient.Builder builder,
            @Value("${external.base-url}") String baseUrl,
            @Value("${external.pool.max-connections:200}") int maxConnections,
            @Value("${external.pool.pending-acquire-max-count:1000}") int pendingAcquireMaxCount,
            @Value("${external.pool.pending-acquire-timeout-ms:5000}") long pendingAcquireTimeoutMs,
            @Value("${external.pool.max-idle-time-ms:30000}") long maxIdleTimeMs,
            @Value("${external.pool.max-life-time-ms:300000}") long maxLifeTimeMs,
            @Value("${external.pool.connect-timeout-ms:2000}") int connectTimeoutMs) {

        ConnectionProvider provider = ConnectionProvider.builder("external")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMaxCount)
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
                .maxIdleTime(Duration.ofMillis(maxIdleTimeMs))
                .maxLifeTime(Duration.ofMillis(maxLifeTimeMs))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
