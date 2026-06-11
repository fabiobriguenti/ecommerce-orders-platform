package com.ecommerce.order.infrastructure.idempotency;

import java.time.Instant;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import com.ecommerce.order.infrastructure.persistence.IdempotencyRow;

@Component
public class R2dbcIdempotencyStore implements IdempotencyStore {

    private final R2dbcEntityTemplate template;

    public R2dbcIdempotencyStore(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<StoredResponse> find(String key) {
        return template.select(IdempotencyRow.class)
                .matching(Query.query(Criteria.where("idempotency_key").is(key)))
                .one()
                .map(row -> new StoredResponse(row.getRequestHash(), row.getResponseStatus(), row.getResponseBody()));
    }

    @Override
    public Mono<Void> save(String key, String requestHash, int responseStatus, String responseBody) {
        IdempotencyRow row = new IdempotencyRow();
        row.setIdempotencyKey(key);
        row.setRequestHash(requestHash);
        row.setResponseStatus(responseStatus);
        row.setResponseBody(responseBody);
        row.setCreatedAt(Instant.now());
        return template.insert(row).then();
    }
}
