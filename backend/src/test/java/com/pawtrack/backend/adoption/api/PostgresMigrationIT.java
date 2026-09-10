package com.pawtrack.backend.adoption.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in PostgreSQL migration check. Creates and drops only a fresh, randomly
 * named schema; it never migrates or cleans the development public schema.
 */
@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = ".+")
class PostgresMigrationIT {
    @Test
    void upgradesExistingApplicationFromV3ToV4WithoutLosingData() throws Exception {
        String url = System.getenv("PAWTRACK_TEST_JDBC_URL");
        String user = System.getenv("PAWTRACK_TEST_DB_USER");
        String password = System.getenv("PAWTRACK_TEST_DB_PASSWORD");
        String schema = "pawtrack_verify_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).target("3").load();
        Flyway latest = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).target("4").cleanDisabled(false).load();
        try {
            assertEquals(3, old.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, user, password);
                 var sql = connection.createStatement()) {
                connection.setSchema(schema);
                sql.executeUpdate("insert into cats (name) values ('Migration fixture')");
                sql.executeUpdate("""
                    insert into adoption_applications (cat_id, adopter_name, adopter_email, notes)
                    select id, 'Demo', 'demo@example.com', 'Keep this note' from cats
                    """);
            }
            assertEquals(1, latest.migrate().migrationsExecuted);
            latest.validate();
            try (var connection = DriverManager.getConnection(url, user, password);
                 var sql = connection.createStatement()) {
                connection.setSchema(schema);
                try (var rows = sql.executeQuery("select notes, created_at = updated_at as backfilled from adoption_applications")) {
                    assertTrue(rows.next());
                    assertEquals("Keep this note", rows.getString("notes"));
                    assertTrue(rows.getBoolean("backfilled"));
                    assertFalse(rows.next());
                }
            }
            assertEquals(0, latest.migrate().migrationsExecuted);
        } finally {
            // This Flyway instance is scoped exclusively to the UUID schema created above.
            latest.clean();
        }
    }
}
