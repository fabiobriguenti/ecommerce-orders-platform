package com.ecommerce.order.infrastructure.outbox;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.port.NotificationPort;
import com.ecommerce.order.domain.port.NotificationPort.NotificationMessage;
import com.ecommerce.order.infrastructure.persistence.OutboxRow;

/**
 * Polls the outbox (ADR-04) and delivers unpublished events to the Notification service, marking
 * them published only after a successful 202. At-least-once delivery; the simulated Notification
 * service is idempotent. Failures are logged and retried on the next tick.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final R2dbcEntityTemplate template;
    private final NotificationPort notificationPort;
    private final int batchSize;

    public OutboxPoller(R2dbcEntityTemplate template, NotificationPort notificationPort,
                        @org.springframework.beans.factory.annotation.Value("${outbox.batch-size:50}") int batchSize) {
        this.template = template;
        this.notificationPort = notificationPort;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void poll() {
        unpublished()
                .flatMap(this::deliver)
                .onErrorContinue((error, item) -> log.warn("Outbox delivery failed, will retry next tick", error))
                .subscribe();
    }

    private reactor.core.publisher.Flux<OutboxRow> unpublished() {
        return template.select(OutboxRow.class)
                .matching(Query.query(Criteria.where("published_at").isNull())
                        .sort(Sort.by("created_at").ascending())
                        .limit(batchSize))
                .all();
    }

    private Mono<Void> deliver(OutboxRow row) {
        return notificationPort.send(new NotificationMessage(row.getAggregateId(), row.getType()))
                .then(markPublished(row));
    }

    private Mono<Void> markPublished(OutboxRow row) {
        row.setPublishedAt(Instant.now());
        return template.update(row).then();
    }
}
