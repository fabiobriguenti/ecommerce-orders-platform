package com.ecommerce.order.infrastructure.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ecommerce.order.infrastructure.idempotency.IdempotencyStore;

/**
 * Honours the {@code Idempotency-Key} header on mutating ({@code POST}/{@code DELETE}) requests.
 *
 * <p>First call with a given key runs normally and its successful (2xx) response is persisted keyed
 * by the request fingerprint. Replays with the same key short-circuit and return the stored
 * response, so retries never produce duplicate side effects. Reusing a key with a different request
 * payload is rejected with {@code 409 Conflict} (RFC 7807), as recommended by the IETF
 * Idempotency-Key draft.
 */
@Component
public class IdempotencyKeyFilter implements WebFilter, Ordered {

    public static final String HEADER = "Idempotency-Key";
    private static final byte[] EMPTY = new byte[0];

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyKeyFilter(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        // Run before the dispatcher handler but leave room for security/observability filters.
        return -10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String key = request.getHeaders().getFirst(HEADER);
        boolean mutating = HttpMethod.POST.equals(method) || HttpMethod.DELETE.equals(method);
        if (key == null || key.isBlank() || !mutating) {
            return chain.filter(exchange);
        }

        return DataBufferUtils.join(request.getBody())
                .map(IdempotencyKeyFilter::toBytes)
                .defaultIfEmpty(EMPTY)
                .flatMap(body -> {
                    String hash = fingerprint(method.name(), request.getURI().getRawPath(), body);
                    return store.find(key)
                            .flatMap(stored -> replay(exchange, stored, hash))
                            .switchIfEmpty(Mono.defer(() -> proceedAndCapture(exchange, chain, key, hash, body)));
                });
    }

    /** Returns the stored response, or 409 if the key was reused with a different payload. */
    private Mono<Void> replay(ServerWebExchange exchange, IdempotencyStore.StoredResponse stored, String hash) {
        ServerHttpResponse response = exchange.getResponse();
        if (!stored.requestHash().equals(hash)) {
            return writeConflict(response);
        }
        response.setRawStatusCode(stored.responseStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = stored.responseBody() == null ? EMPTY : stored.responseBody().getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /** Replays the consumed request body downstream and captures the response for storage. */
    private Mono<Void> proceedAndCapture(ServerWebExchange exchange, WebFilterChain chain,
                                         String key, String hash, byte[] body) {
        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return body.length == 0
                        ? Flux.empty()
                        : Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
        ServerHttpResponse decoratedResponse = new CapturingResponse(exchange.getResponse(), key, hash);
        return chain.filter(exchange.mutate().request(decoratedRequest).response(decoratedResponse).build());
    }

    private Mono<Void> writeConflict(ServerHttpResponse response) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The Idempotency-Key was already used with a different request payload");
        problem.setTitle("Idempotency conflict");
        problem.setType(URI.create("https://api.ecommerce-orders.com/problems/idempotency-conflict"));
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (Exception e) {
            body = EMPTY;
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /** Response decorator that buffers the body and stores it (on 2xx) under the idempotency key. */
    private final class CapturingResponse extends ServerHttpResponseDecorator {

        private final String key;
        private final String hash;

        private CapturingResponse(ServerHttpResponse delegate, String key, String hash) {
            super(delegate);
            this.key = key;
            this.hash = hash;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return DataBufferUtils.join(Flux.from(body)).flatMap(joined -> {
                byte[] content = toBytes(joined);
                int status = getStatusCode() == null ? HttpStatus.OK.value() : getStatusCode().value();
                Mono<Void> write = getDelegate().writeWith(Mono.just(bufferFactory().wrap(content)));
                if (status >= 200 && status < 300) {
                    String payload = new String(content, StandardCharsets.UTF_8);
                    // A duplicate-key race (two concurrent first-calls) loses the insert harmlessly.
                    return store.save(key, hash, status, payload)
                            .onErrorResume(e -> Mono.empty())
                            .then(write);
                }
                return write;
            });
        }
    }

    private static byte[] toBytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }

    private static String fingerprint(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ' ');
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
