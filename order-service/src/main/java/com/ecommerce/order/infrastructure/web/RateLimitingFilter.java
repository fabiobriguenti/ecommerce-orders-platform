package com.ecommerce.order.infrastructure.web;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Lightweight per-client fixed-window rate limiter (OWASP API4 — unrestricted resource
 * consumption). It runs ahead of the security chain so it also shields the token endpoint. Disabled
 * with {@code app.rate-limit.enabled=false}; the window is one minute and the cap is configurable.
 *
 * <p>In-memory and per-instance by design (KISS): adequate for a single-node challenge deployment.
 * A distributed setup would back this with Redis, but that is out of scope here.
 */
@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingFilter implements WebFilter, Ordered {

    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final int limitPerMinute;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitingFilter(@Value("${app.rate-limit.requests-per-minute:120}") int limitPerMinute,
                              ObjectMapper objectMapper) {
        this.limitPerMinute = limitPerMinute;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        // Ahead of Spring Security's WebFilterChainProxy (order -100).
        return -150;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String client = clientKey(exchange.getRequest());
        long minute = System.currentTimeMillis() / 60_000L;

        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.clear();
        }
        Window window = windows.compute(client, (k, current) ->
                (current == null || current.minute != minute) ? new Window(minute) : current);
        int count = window.count.incrementAndGet();

        if (count > limitPerMinute) {
            return tooManyRequests(exchange);
        }
        return chain.filter(exchange);
    }

    private String clientKey(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddress() == null
                ? "unknown"
                : request.getRemoteAddress().getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit of " + limitPerMinute + " requests per minute exceeded");
        problem.setTitle("Too many requests");
        problem.setType(URI.create("https://api.ecommerce-orders.com/problems/rate-limit-exceeded"));

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().add("Retry-After", "60");
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (Exception e) {
            bytes = new byte[0];
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
