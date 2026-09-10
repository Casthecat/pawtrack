package com.pawtrack.backend.care.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in PostgreSQL check for V5. It creates and removes only a fresh,
 * randomly named schema and never migrates the development public schema.
 */
@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = ".+")
class CareRecordPostgresMigrationIT {

    @Test
    void upgradesV4ToV5_andPersistsCareRecord() throws Exception {
        String url = System.getenv("PAWTRACK_TEST_JDBC_URL");
        String user = System.getenv("PAWTRACK_TEST_DB_USER");
        String password = System.getenv("PAWTRACK_TEST_DB_PASSWORD");
        String schema = "pawtrack_care_verify_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).target("4").load();
        Flyway latest = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).target("5").cleanDisabled(false).load();

        try {
            assertEquals(4, old.migrate().migrationsExecuted);
            assertEquals(1, latest.migrate().migrationsExecuted);
            latest.validate();

            try (var connection = DriverManager.getConnection(url, user, password);
                 var sql = connection.createStatement()) {
                connection.setSchema(schema);
                sql.executeUpdate("insert into cats (name) values ('Nori')");
                sql.executeUpdate("""
                        insert into care_records (cat_id, type, note)
                        select id, 'CHECKUP', 'Temperature rechecked.' from cats where name = 'Nori'
                        """);
                try (var rows = sql.executeQuery("select type, note, created_at from care_records")) {
                    assertTrue(rows.next());
                    assertEquals("CHECKUP", rows.getString("type"));
                    assertEquals("Temperature rechecked.", rows.getString("note"));
                    assertTrue(rows.getTimestamp("created_at").toInstant().toEpochMilli() > 0);
                    assertFalse(rows.next());
                }
            }
            assertEquals(0, latest.migrate().migrationsExecuted);
        } finally {
            latest.clean();
        }
    }
}
