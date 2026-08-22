package com.github.spud.tinystore.tests.api.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the E2E seed fixture (seed/e2e-seed.sql) directly against the compose-test
 * PostgreSQL, outside the core Flyway migration chain.
 *
 * <p>Connection defaults to the compose-test Postgres (host 5433, db tinystore,
 * user/password postgres) and can be overridden via {@code POSTGRES_URL}, {@code POSTGRES_USER},
 * {@code POSTGRES_PASSWORD} system properties. Idempotent via {@code ON CONFLICT DO NOTHING}.
 */
public final class SeedData {

    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    private static final String SEED_RESOURCE = "/seed/e2e-seed.sql";

    private SeedData() {
    }

    public static void applyIfMissing() {
        String url = System.getProperty("POSTGRES_URL",
            "jdbc:postgresql://localhost:5433/tinystore");
        String user = System.getProperty("POSTGRES_USER", "postgres");
        String password = System.getProperty("POSTGRES_PASSWORD", "postgres");

        String sql = loadSql();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {

            int applied = 0;
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                statement.execute(trimmed);
                applied++;
            }
            log.info("Applied E2E seed fixture ({} statements) to {}", applied, url);

        } catch (Exception e) {
            // Fail-fast: E2E cannot proceed without the seed contract.
            throw new IllegalStateException(
                "E2E seed fixture failed to apply at " + url + ": " + e.getMessage(), e);
        }
    }

    private static String loadSql() {
        try (InputStream in = SeedData.class.getResourceAsStream(SEED_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Seed resource not found: " + SEED_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed resource: " + SEED_RESOURCE, e);
        }
    }
}
