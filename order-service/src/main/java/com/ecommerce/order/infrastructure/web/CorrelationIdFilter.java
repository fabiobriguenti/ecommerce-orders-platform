package com.ecommerce.order.infrastructure.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Correlation + access logging (ADR-16).
 *
 * <p>Each request gets a correlation id — the inbound {@code X-Correlation-Id} header when present,
 * otherwise a generated UUID — which is echoed back on the response and written onto a single
 * structured access-log line. The id is placed in the MDC explicitly at log time, so it lands in
 * the JSON output regardless of reactive thread hops. Distributed propagation across services is
 * handled by Micrometer tracing (W3C {@code traceparent}); its {@code traceId}/{@code spanId} also
 * appear on log lines emitted within the trace scope.
 */
@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (inbound != null && !inbound.isBlank()) ? inbound : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        long startNanos = System.nanoTime();

        return chain.filter(exchange).doOnEach(signal -> {
            if (signal.isOnComplete() || signal.isOnError()) {
                long tookMs = (System.nanoTime() - startNanos) / 1_000_000L;
                Integer status = exchange.getResponse().getStatusCode() == null
                        ? null : exchange.getResponse().getStatusCode().value();
                MDC.put(MDC_KEY, correlationId);
                try {
                    if (signal.isOnError()) {
                        log.warn("{} {} -> {} ({} ms) failed: {}", method, path, status, tookMs,
                                signal.getThrowable() == null ? "unknown" : signal.getThrowable().toString());
                    } else {
                        log.info("{} {} -> {} ({} ms)", method, path, status, tookMs);
                    }
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }
        });
    }
}
