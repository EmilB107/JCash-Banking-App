package com.jcash.app.common;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.SQLException;

// Shared by all PostgreSQL integration tests; identical properties and imports
// let them reuse one cached context, so the test database is reset once per run.
@TestConfiguration
public class FreshTestDatabaseConfiguration {

    public static final String TEST_DATABASE = "jcash_banking_test_db";
    public static final String DATASOURCE_URL =
            "spring.datasource.url=jdbc:postgresql://localhost:5432/" + TEST_DATABASE;
    public static final String CLEAN_ENABLED = "spring.flyway.clean-disabled=false";

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            requireTestDatabase(flyway);
            flyway.clean();
            flyway.migrate();
        };
    }

    private static void requireTestDatabase(Flyway flyway) {
        try (Connection connection =
                     flyway.getConfiguration().getDataSource().getConnection()) {
            if (!TEST_DATABASE.equals(connection.getCatalog())) {
                throw new IllegalStateException(
                        "Refusing to clean non-test database " + connection.getCatalog());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
