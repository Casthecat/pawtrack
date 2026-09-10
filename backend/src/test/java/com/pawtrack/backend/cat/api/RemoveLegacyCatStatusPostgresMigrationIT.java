package com.pawtrack.backend.cat.api;

import com.pawtrack.backend.cat.api.mapper.CatMapper;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = "jdbc:postgresql:.+")
class RemoveLegacyCatStatusPostgresMigrationIT {
    private static final String URL = System.getenv("PAWTRACK_TEST_JDBC_URL");
    private static final String USER = System.getenv("PAWTRACK_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("PAWTRACK_TEST_DB_PASSWORD");

    @Test
    void v8PreservesMigratedAndIndependentlyChangedTypedStatesAndValidatesJpa() throws Exception {
        String schema = schema();
        Flyway v8 = migration(schema, "8");
        List<String> legacy = List.of("NORMAL", "ADOPTABLE", "UNDER_OBSERVATION", "SICK", "ADOPTED");
        List<CatHealthStatus> expectedHealth = List.of(CatHealthStatus.NORMAL, CatHealthStatus.NORMAL,
                CatHealthStatus.UNDER_OBSERVATION, CatHealthStatus.SICK, CatHealthStatus.NORMAL,
                CatHealthStatus.UNDER_OBSERVATION, CatHealthStatus.SICK);
        try {
            assertEquals(6, migration(schema, "6").migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.setSchema(schema);
                try (var insert = connection.prepareStatement("insert into cats(name,status) values (?,?)")) {
                    for (String value : legacy) {
                        insert.setString(1, "Legacy " + value);
                        insert.setString(2, value);
                        insert.executeUpdate();
                    }
                }
                assertEquals(1, migration(schema, "7").migrate().migrationsExecuted);
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("""
                            insert into cats(name,health_status,adoption_status) values
                            ('Adopted observation','UNDER_OBSERVATION','ADOPTED'),
                            ('Adopted sick','SICK','ADOPTED')
                            """);
                    // This archive is deliberately wrong: V8 must never re-backfill from it.
                    sql.executeUpdate("update cats set status = 'STALE_ARCHIVE'");
                }
                var before = typedRows(connection);
                assertEquals(1, v8.migrate().migrationsExecuted);
                assertEquals(before, typedRows(connection));
                assertFalse(hasLegacyColumn(connection));
                v8.validate();
                assertEquals(0, v8.migrate().migrationsExecuted);
            }
            try (var context = new SpringApplicationBuilder(com.pawtrack.backend.support.PreIdentityMigrationApplication.class)
                    .web(WebApplicationType.NONE).profiles("postgres-migration")
                    .run("--spring.datasource.url=" + URL,
                            "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD,
                            "--spring.datasource.driver-class-name=org.postgresql.Driver",
                            "--spring.datasource.hikari.schema=" + schema,
                            "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                            "--spring.flyway.enabled=true", "--spring.flyway.target=8",
                            "--spring.jpa.hibernate.ddl-auto=validate",
                            "--spring.jpa.properties.hibernate.default_schema=" + schema,
                            "--spring.jpa.open-in-view=false", "--logging.level.org.hibernate.SQL=INFO")) {
                var cats = context.getBean(CatRepository.class);
                for (int i = 0; i < expectedHealth.size(); i++) {
                    var cat = cats.findById((long) i + 1).orElseThrow();
                    var expectedAdoption = i >= 4 ? CatAdoptionStatus.ADOPTED : CatAdoptionStatus.AVAILABLE;
                    assertEquals(expectedHealth.get(i), cat.getHealthStatus());
                    assertEquals(expectedAdoption, cat.getAdoptionStatus());
                    assertEquals(expectedHealth.get(i), CatMapper.toResponse(cat).getHealthStatus());
                    assertEquals(expectedAdoption, CatMapper.toDetailResponse(cat, null, false).getAdoptionStatus());
                }
                var saved = cats.saveAndFlush(new com.pawtrack.backend.cat.domain.Cat("After V8"));
                assertEquals(CatHealthStatus.NORMAL, saved.getHealthStatus());
                assertEquals(CatAdoptionStatus.AVAILABLE, saved.getAdoptionStatus());
            }
        } finally {
            v8.clean();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"health_status", "adoption_status"})
    void refusesToDropArchiveWhenTypedSchemaIsNullable(String column) throws Exception {
        String schema = schema();
        Flyway v8 = migration(schema, "8");
        try {
            migration(schema, "7").migrate();
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement()) {
                    // Column comes solely from the fixed parameterized-test allowlist above.
                    sql.execute("alter table cats alter column " + column + " drop not null");
                }
                var failure = assertThrows(FlywayException.class, v8::migrate);
                assertTrue(failure.getMessage().contains("typed columns must be non-null"));
                assertTrue(hasLegacyColumn(connection));
            }
        } finally {
            v8.clean();
        }
    }

    private List<String> typedRows(java.sql.Connection connection) throws Exception {
        var values = new java.util.ArrayList<String>();
        try (var sql = connection.createStatement();
             var rows = sql.executeQuery("select id, health_status, adoption_status from cats order by id")) {
            while (rows.next()) values.add(rows.getLong(1) + ":" + rows.getString(2) + ":" + rows.getString(3));
        }
        return values;
    }

    private boolean hasLegacyColumn(java.sql.Connection connection) throws Exception {
        try (var sql = connection.createStatement(); var columns = sql.executeQuery("""
                select count(*) from information_schema.columns where table_schema = current_schema()
                and table_name = 'cats' and column_name = 'status'
                """)) {
            assertTrue(columns.next());
            return columns.getInt(1) != 0;
        }
    }

    private String schema() { return "pawtrack_v8_verify_" + UUID.randomUUID().toString().replace("-", ""); }

    private Flyway migration(String schema, String version) {
        return Flyway.configure().dataSource(URL, USER, PASSWORD).schemas(schema).defaultSchema(schema)
                .target(version).cleanDisabled(false).load();
    }
}
