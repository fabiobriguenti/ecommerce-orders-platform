package com.ecommerce.order.infrastructure.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.infrastructure.persistence.AbstractPostgresIT;

class IdempotencyStoreIT extends AbstractPostgresIT {

    @Test
    void savesAndFindsStoredResponse() {
        R2dbcIdempotencyStore store = new R2dbcIdempotencyStore(template);

        StepVerifier.create(store.save("key-1", "hash-1", 201, "{\"id\":\"x\"}"))
                .verifyComplete();

        StepVerifier.create(store.find("key-1"))
                .assertNext(stored -> {
                    assertThat(stored.requestHash()).isEqualTo("hash-1");
                    assertThat(stored.responseStatus()).isEqualTo(201);
                    assertThat(stored.responseBody()).contains("\"id\"");
                })
                .verifyComplete();
    }

    @Test
    void missingKeyReturnsEmpty() {
        R2dbcIdempotencyStore store = new R2dbcIdempotencyStore(template);
        StepVerifier.create(store.find("absent")).verifyComplete();
    }
}
