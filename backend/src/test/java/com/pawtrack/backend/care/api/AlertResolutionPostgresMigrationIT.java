package com.pawtrack.backend.care.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = ".+")
class AlertResolutionPostgresMigrationIT {
    @Test
    void upgradesV5ToV6PreservingHistoryAndOptionalCareLinks() throws Exception {
        String url = System.getenv("PAWTRACK_TEST_JDBC_URL");
        String user = System.getenv("PAWTRACK_TEST_DB_USER");
        String password = System.getenv("PAWTRACK_TEST_DB_PASSWORD");
        String schema = "pawtrack_resolution_verify_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).target("5").load();
        Flyway current = Flyway.configure().dataSource(url, user, password)
                .schemas(schema).defaultSchema(schema).target("6").cleanDisabled(false).load();
        try {
            assertEquals(5, old.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, user, password);
                 var sql = connection.createStatement()) {
                connection.setSchema(schema);
                sql.executeUpdate("insert into cats (id, name) values (1, 'Nori')");
                sql.executeUpdate("""
                        insert into alerts (id, cat_id, type, status, created_at)
                        values (1, 1, 'FEVER', 'CLOSED', '2026-01-01T12:00:00Z'),
                               (2, 1, 'FEVER', 'OPEN', '2026-01-02T12:00:00Z')
                        """);
                sql.executeUpdate("insert into care_records (cat_id, type, note) values (1, 'FEEDING', 'Existing generic note')");
                assertEquals(1, current.migrate().migrationsExecuted);
                current.validate();
                try (var rows = sql.executeQuery("select resolved_at, created_at from alerts where id = 1")) {
                    assertTrue(rows.next());
                    assertNull(rows.getObject("resolved_at"));
                    assertEquals("2026-01-01T12:00:00Z", rows.getTimestamp("created_at").toInstant().toString());
                }
                try (var rows = sql.executeQuery("select note, alert_id from care_records")) {
                    assertTrue(rows.next());
                    assertEquals("Existing generic note", rows.getString("note"));
                    assertNull(rows.getObject("alert_id"));
                }
                sql.executeUpdate("update alerts set status = 'CLOSED', resolved_at = now() where id = 2");
                sql.executeUpdate("""
                        insert into care_records (cat_id, alert_id, type, note, created_at)
                        select cat_id, id, 'CHECKUP', 'Resolution note', resolved_at from alerts where id = 2
                        """);
                try (var rows = sql.executeQuery("""
                        select a.status, a.resolved_at = c.created_at as same_time
                        from alerts a join care_records c on c.alert_id = a.id where a.id = 2
                        """)) {
                    assertTrue(rows.next());
                    assertEquals("CLOSED", rows.getString("status"));
                    assertTrue(rows.getBoolean("same_time"));
                }
                assertThrows(java.sql.SQLException.class, () -> sql.executeUpdate("""
                        insert into care_records (cat_id, alert_id, type, note)
                        values (1, 99999, 'OTHER', 'Invalid alert link')
                        """));
                sql.executeUpdate("delete from alerts where id = 2");
                try (var rows = sql.executeQuery("select alert_id from care_records where note = 'Resolution note'")) {
                    assertTrue(rows.next());
                    assertNull(rows.getObject("alert_id"));
                }
                try (var rows = sql.executeQuery("select count(*) from pg_indexes where schemaname = current_schema() and indexname = 'idx_care_records_alert'")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
            }
            assertEquals(0, current.migrate().migrationsExecuted);
        } finally {
            // Only this test's newly created UUID schema can be cleaned.
            current.clean();
        }
    }
}
