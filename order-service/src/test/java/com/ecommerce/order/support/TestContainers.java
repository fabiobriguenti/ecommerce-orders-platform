package com.ecommerce.order.support;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Single source of truth for the integration-test containers. Both are static singletons started
 * once per JVM and shared by every {@code *IT} — the persistence/external slice tests and the
 * full-context acceptance tests — so a {@code mvn verify} run spins up exactly one Postgres and one
 * WireMock instead of one pair per base class.
 *
 * <p>{@code withReuse(true)} additionally lets the containers survive across local runs when the
 * developer opts in via {@code ~/.testcontainers.properties} ({@code testcontainers.reuse.enable=true}).
 * It is a deliberate no-op in CI, where that file is absent and the containers are torn down with the
 * runner — so reuse never leaks state into a pipeline.
 */
public final class TestContainers {

    public static final PostgreSQLContainer<?> POSTGRES;
    public static final GenericContainer<?> WIREMOCK;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("orders")
                .withUsername("orders")
                .withPassword("orders")
                .withReuse(true);
        POSTGRES.start();
        // Apply the production migrations once over JDBC so the slice tests (which talk straight to
        // R2DBC, bypassing the Spring context) see the full schema. The acceptance tests let the
        // application's own Flyway run; on this already-migrated database it is a no-op.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.9.1")
                .withExposedPorts(8080)
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../wiremock/mappings"), "/home/wiremock/mappings")
                .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200))
                .withReuse(true);
        WIREMOCK.start();
    }

    private TestContainers() {
    }

    /** Base URL of the WireMock-simulated external services (Customer, Catalog, Payment, Notification). */
    public static String wiremockBaseUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }
}
