package com.ecommerce.order.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Shared singleton Postgres container for the persistence integration tests. The container starts
 * once per JVM; Flyway migrations are applied against it (JDBC), and each test gets a fresh
 * {@link R2dbcEntityTemplate} with truncated tables.
 */
public abstract class AbstractPostgresIT {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("orders")
                .withUsername("orders")
                .withPassword("orders");
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

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
