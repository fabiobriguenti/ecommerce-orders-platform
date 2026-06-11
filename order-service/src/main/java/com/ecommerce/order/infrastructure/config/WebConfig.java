package com.ecommerce.order.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Web-layer wiring (Phase 6). Exposes a {@link TransactionalOperator} so the controllers can run
 * each mutating use case inside a single reactive transaction, committing the aggregate change and
 * its outbox event atomically (ADR-04). The {@link ReactiveTransactionManager} itself is
 * auto-configured by Spring Boot from the R2DBC connection factory.
 */
@Configuration
public class WebConfig {

    @Bean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
