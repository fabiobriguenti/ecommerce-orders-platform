package com.ecommerce.order.infrastructure.idempotency;

import reactor.core.publisher.Mono;

/**
 * Stores responses keyed by {@code Idempotency-Key} so replays of mutating requests return the
 * original outcome. Consumed by the web filter in Phase 6.
 */
public interface IdempotencyStore {

    Mono<StoredResponse> find(String key);

    Mono<Void> save(String key, String requestHash, int responseStatus, String responseBody);

    record StoredResponse(String requestHash, int responseStatus, String responseBody) {
    }
}
