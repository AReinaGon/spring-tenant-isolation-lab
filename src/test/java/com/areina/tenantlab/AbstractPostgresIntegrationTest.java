package com.areina.tenantlab;

import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts one shared PostgreSQL container for the whole test JVM and exposes its coordinates
 * as Spring properties. Each concrete test selects its deployment with
 * {@code @ActiveProfiles("naive")} or {@code @ActiveProfiles("secured")}; both profiles point
 * at this same container but at different schemas and Flyway migration sets.
 *
 * <p>The container bootstrap script (see {@code src/test/resources/db/bootstrap.sql}) runs as
 * the container superuser and creates the {@code migrator} (migration/owner) and
 * {@code app_user} (runtime, no bypass) roles.</p>
 */
public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("lab")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("db/bootstrap.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("db.host", POSTGRES::getHost);
        registry.add("db.port", () -> POSTGRES.getMappedPort(5432));
        registry.add("db.name", POSTGRES::getDatabaseName);
        registry.add("db.app.user", () -> "app_user");
        registry.add("db.app.password", () -> "app_user");
        registry.add("db.migrator.user", () -> "migrator");
        registry.add("db.migrator.password", () -> "migrator");
    }

    /**
     * A connection as the container superuser, which bypasses Row-Level Security. Used to
     * demonstrate the honest caveat that a superuser/BYPASSRLS role sees all rows.
     */
    protected static Connection superuserConnection() throws SQLException {
        return POSTGRES.createConnection("");
    }
}
