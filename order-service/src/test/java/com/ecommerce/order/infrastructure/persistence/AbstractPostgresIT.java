package com.ecommerce.order.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.ecommerce.order.support.TestContainers;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Persistence slice tests talk straight to R2DBC (no Spring context) against the shared singleton
 * Postgres in {@link TestContainers} (started once per JVM, migrated with Flyway). Each test gets a
 * fresh {@link R2dbcEntityTemplate} with truncated tables.
 */
public abstract class AbstractPostgresIT {

    protected static final PostgreSQLContainer<?> POSTGRES = TestContainers.POSTGRES;

    protected R2dbcEntityTemplate template;

    @BeforeEach
    void initTemplate() {
        ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(POSTGRES.getHost())
                        .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                        .database(POSTGRES.getDatabaseName())
                        .username(POSTGRES.getUsername())
                        .password(POSTGRES.getPassword())
                        .build());
        template = new R2dbcEntityTemplate(connectionFactory);
        template.getDatabaseClient()
                .sql("TRUNCATE orders, order_items, payments, outbox, idempotency_keys CASCADE")
                .fetch().rowsUpdated().block();
    }
}
