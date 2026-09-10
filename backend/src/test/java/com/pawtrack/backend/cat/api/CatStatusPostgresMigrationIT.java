package com.pawtrack.backend.cat.api;

import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.service.AdoptionService;
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
class CatStatusPostgresMigrationIT {
    private static final String URL = System.getenv("PAWTRACK_TEST_JDBC_URL");
    private static final String USER = System.getenv("PAWTRACK_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("PAWTRACK_TEST_DB_PASSWORD");

    @Test
    void backfillsAllKnownValuesAndValidatesJpaWithoutReadingLegacyState() throws Exception {
        String schema = schema();
        Flyway migration = migration(schema, "7");
        List<String> legacy = List.of("NORMAL", "ADOPTABLE", "UNDER_OBSERVATION", "SICK", "ADOPTED");
        List<CatHealthStatus> health = List.of(CatHealthStatus.NORMAL, CatHealthStatus.NORMAL,
                CatHealthStatus.UNDER_OBSERVATION, CatHealthStatus.SICK, CatHealthStatus.NORMAL);
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
                assertEquals(1, migration.migrate().migrationsExecuted);
                migration.validate();
                try (var sql = connection.createStatement();
                     var rows = sql.executeQuery("select status, health_status, adoption_status from cats order by id")) {
                    for (int i = 0; i < legacy.size(); i++) {
                        assertTrue(rows.next());
                        assertEquals(legacy.get(i), rows.getString("status"));
                        assertEquals(health.get(i).name(), rows.getString("health_status"));
                        assertEquals(i == 4 ? "ADOPTED" : "AVAILABLE", rows.getString("adoption_status"));
                    }
                    assertFalse(rows.next());
                }
                try (var sql = connection.createStatement(); var columns = sql.executeQuery("""
                        select column_name, is_nullable, data_type from information_schema.columns
                        where table_schema = current_schema() and table_name = 'cats'
                          and column_name in ('health_status','adoption_status')
                        """)) {
                    int count = 0;
                    while (columns.next()) {
                        count++;
                        assertEquals("NO", columns.getString("is_nullable"));
                        assertEquals("character varying", columns.getString("data_type"));
                    }
                    assertEquals(2, count);
                }
                // Deliberately contradict the archived column after migration. The typed
                // backend must still read NORMAL/AVAILABLE, accept and approve an application.
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("update cats set status = 'LEGACY_ONLY_TEST_VALUE' where id = 1");
                }
            }

            try (var context = new SpringApplicationBuilder(BackendApplication.class)
                    .web(WebApplicationType.NONE).profiles("postgres-migration")
                    .run("--spring.datasource.url=" + URL,
                            "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD,
                            "--spring.datasource.driver-class-name=org.postgresql.Driver",
                            "--spring.datasource.hikari.schema=" + schema,
                            "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                            "--spring.flyway.enabled=true", "--spring.flyway.target=7", "--spring.jpa.hibernate.ddl-auto=validate",
                            "--spring.jpa.properties.hibernate.default_schema=" + schema,
                            "--spring.jpa.open-in-view=false", "--logging.level.org.hibernate.SQL=INFO")) {
                var cats = context.getBean(CatRepository.class);
                for (int i = 0; i < legacy.size(); i++) {
                    var cat = cats.findById((long) i + 1).orElseThrow();
                    assertEquals(health.get(i), cat.getHealthStatus());
                    assertEquals(i == 4 ? CatAdoptionStatus.ADOPTED : CatAdoptionStatus.AVAILABLE, cat.getAdoptionStatus());
                    assertEquals(health.get(i), CatMapper.toResponse(cat).getHealthStatus());
                    assertEquals(cat.getAdoptionStatus(), CatMapper.toResponse(cat).getAdoptionStatus());
                    assertEquals(health.get(i), CatMapper.toDetailResponse(cat, null, false).getHealthStatus());
                    assertEquals(cat.getAdoptionStatus(), CatMapper.toDetailResponse(cat, null, false).getAdoptionStatus());
                }
                var adoption = context.getBean(AdoptionService.class);
                var request = new AdoptionApplicationRequest();
                request.setCatId(1L);
                request.setAdopterName("Migration verifier");
                request.setAdopterEmail("migration@example.com");
                adoption.approveApplication(adoption.submitApplication(request).id());
                var adopted = cats.findById(1L).orElseThrow();
                assertEquals(CatAdoptionStatus.ADOPTED, adopted.getAdoptionStatus());
                assertEquals(CatHealthStatus.NORMAL, adopted.getHealthStatus());
                assertEquals(CatAdoptionStatus.ADOPTED, CatMapper.toResponse(adopted).getAdoptionStatus());
            }
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement();
                     var row = sql.executeQuery("select status, adoption_status from cats where id = 1")) {
                    assertTrue(row.next());
                    assertEquals("LEGACY_ONLY_TEST_VALUE", row.getString("status"));
                    assertEquals("ADOPTED", row.getString("adoption_status"));
                }
            }
            assertEquals(0, migration.migrate().migrationsExecuted);
        } finally {
            migration.clean();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANUAL_HOLD", "normal", ""})
    void unknownLegacyValuesFailWithoutPartialBackfillAndAllowExplicitRepair(String unknown) throws Exception {
        String schema = schema();
        Flyway migration = migration(schema, "7");
        try {
            migration(schema, "6").migrate();
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.setSchema(schema);
                try (var insert = connection.prepareStatement("insert into cats(name,status) values ('Unknown fixture',?)")) {
                    insert.setString(1, unknown);
                    insert.executeUpdate();
                }
                var failure = assertThrows(FlywayException.class, migration::migrate);
                assertTrue(failure.getMessage().contains("unsupported legacy status"));
                try (var sql = connection.createStatement(); var columns = sql.executeQuery("""
                        select count(*) from information_schema.columns where table_schema = current_schema()
                        and table_name = 'cats' and column_name in ('health_status','adoption_status')
                        """)) {
                    assertTrue(columns.next());
                    assertEquals(0, columns.getInt(1));
                }
                try (var sql = connection.createStatement(); var row = sql.executeQuery("select status from cats")) {
                    assertTrue(row.next());
                    assertEquals(unknown, row.getString(1));
                }
                // A deliberate repair in this disposable fixture, never an automatic fallback.
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("update cats set status = 'SICK'");
                }
                assertEquals(1, migration.migrate().migrationsExecuted);
                try (var sql = connection.createStatement(); var row = sql.executeQuery("select health_status, adoption_status from cats")) {
                    assertTrue(row.next());
                    assertEquals("SICK", row.getString(1));
                    assertEquals("AVAILABLE", row.getString(2));
                }
            }
        } finally {
            migration.clean();
        }
    }

    private String schema() { return "pawtrack_status_verify_" + UUID.randomUUID().toString().replace("-", ""); }

    private Flyway migration(String schema, String version) {
        return Flyway.configure().dataSource(URL, USER, PASSWORD).schemas(schema).defaultSchema(schema)
                .target(version).cleanDisabled(false).load();
    }
}
