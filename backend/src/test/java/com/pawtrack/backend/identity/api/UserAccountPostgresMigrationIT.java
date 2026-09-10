package com.pawtrack.backend.identity.api;

import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = "jdbc:postgresql:.+")
class UserAccountPostgresMigrationIT {
    @Test void v8ToV9PreservesCatsAndValidatesIdentitySchemaAndConstraints() throws Exception {
        String url = System.getenv("PAWTRACK_TEST_JDBC_URL");
        String user = System.getenv("PAWTRACK_TEST_DB_USER");
        String password = System.getenv("PAWTRACK_TEST_DB_PASSWORD");
        String schema = "pawtrack_identity_verify_" + UUID.randomUUID().toString().replace("-", "");
        var v9 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .target("9").cleanDisabled(false).load();
        try {
            var v8 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("8").load();
            assertEquals(8, v8.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("insert into cats(name,health_status,adoption_status) values ('Existing adopted cat','SICK','ADOPTED')");
                }
                assertEquals(1, v9.migrate().migrationsExecuted);
                v9.validate();
                var expectedTypes = Map.of("id", "bigint", "email", "character varying", "display_name", "character varying",
                        "password_hash", "character varying", "role", "character varying",
                        "created_at", "timestamp with time zone", "updated_at", "timestamp with time zone");
                var expectedLengths = Map.of("email", 200, "display_name", 120, "password_hash", 255, "role", 20);
                try (var sql = connection.createStatement(); var columns = sql.executeQuery("""
                        select column_name, data_type, is_nullable, character_maximum_length
                        from information_schema.columns where table_schema = current_schema() and table_name = 'user_accounts'
                        """)) {
                    int count = 0;
                    while (columns.next()) {
                        count++;
                        String name = columns.getString(1);
                        assertEquals(expectedTypes.get(name), columns.getString(2));
                        assertEquals("NO", columns.getString(3));
                        if (expectedLengths.containsKey(name)) assertEquals(expectedLengths.get(name).intValue(), columns.getInt(4));
                    }
                    assertEquals(7, count);
                }
                try (var sql = connection.createStatement(); var cat = sql.executeQuery("select health_status, adoption_status from cats")) {
                    assertTrue(cat.next());
                    assertEquals("SICK", cat.getString(1));
                    assertEquals("ADOPTED", cat.getString(2));
                }
            }
            try (var context = new SpringApplicationBuilder(BackendApplication.class).web(WebApplicationType.NONE)
                    .profiles("postgres-migration").run("--spring.datasource.url=" + url,
                            "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                            "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.datasource.hikari.schema=" + schema,
                            "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                            "--spring.flyway.enabled=true", "--spring.flyway.target=10",
                            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                            "--spring.jpa.properties.hibernate.default_schema=" + schema,
                            "--logging.level.org.hibernate.SQL=INFO")) {
                var accounts = context.getBean(UserAccountRepository.class);
                var identity = context.getBean(IdentityService.class);
                assertEquals(0, accounts.count()); // The non-demo profile never provisions known passwords.
                for (var role : UserRole.values()) {
                    var account = identity.createAccount(" " + role.name() + "@EXAMPLE.COM ", "PG " + role, "Test-only-pass!", role);
                    var loaded = accounts.findById(account.getId()).orElseThrow();
                    assertEquals(role, loaded.getRole());
                    assertTrue(context.getBean(PasswordEncoder.class).matches("Test-only-pass!", loaded.getPasswordHash()));
                    assertNotNull(loaded.getCreatedAt());
                    assertNotNull(loaded.getUpdatedAt());
                }
                assertThrows(DataIntegrityViolationException.class,
                        () -> identity.createAccount("STAFF@example.com", "Duplicate", "Test-only-pass!", UserRole.STAFF));
            }
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement()) {
                    assertEquals("23514", assertThrows(SQLException.class,
                            () -> sql.executeUpdate("update user_accounts set role = 'ADMIN' where role = 'STAFF'")).getSQLState());
                    assertEquals("23514", assertThrows(SQLException.class,
                            () -> sql.executeUpdate("update user_accounts set email = 'Mixed@EXAMPLE.COM' where role = 'STAFF'")).getSQLState());
                    assertEquals("23502", assertThrows(SQLException.class,
                            () -> sql.executeUpdate("update user_accounts set password_hash = null where role = 'STAFF'")).getSQLState());
                }
            }
            assertEquals(0, v9.migrate().migrationsExecuted);
        } finally { v9.clean(); }
    }
}
