package com.pawtrack.backend.adoption.api;

import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.support.TestAccounts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = "jdbc:postgresql:.+")
class AdoptionOwnershipPostgresMigrationIT {
    @Test void v9ToV10PreservesUnownedHistoryAndValidatesOwnershipForeignKeyAndJpa() throws Exception {
        String url = System.getenv("PAWTRACK_TEST_JDBC_URL");
        String user = System.getenv("PAWTRACK_TEST_DB_USER");
        String password = System.getenv("PAWTRACK_TEST_DB_PASSWORD");
        String schema = "pawtrack_owner_verify_" + UUID.randomUUID().toString().replace("-", "");
        var latest = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .target("10").cleanDisabled(false).load();
        try {
            var v9 = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("9").load();
            assertEquals(9, v9.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("insert into cats(name) values ('Historical companion')");
                    sql.executeUpdate("insert into user_accounts(email,display_name,password_hash,role) values ('owner@example.com','Existing owner','{noop}test-only','ADOPTER')");
                    sql.executeUpdate("insert into adoption_applications(cat_id,adopter_name,adopter_email,notes) values (1,'Historical name','owner@example.com','Retain this note')");
                }
                assertEquals(1, latest.migrate().migrationsExecuted);
                latest.validate();
                assertEquals(0, latest.migrate().migrationsExecuted);
                try (var sql = connection.createStatement(); var row = sql.executeQuery("select adopter_account_id,adopter_name,adopter_email,notes from adoption_applications where id=1")) {
                    assertTrue(row.next()); assertNull(row.getObject(1));
                    assertEquals("Historical name", row.getString(2)); assertEquals("owner@example.com", row.getString(3)); assertEquals("Retain this note", row.getString(4));
                }
                try (var sql = connection.createStatement(); var column = sql.executeQuery("select data_type,is_nullable from information_schema.columns where table_schema=current_schema() and table_name='adoption_applications' and column_name='adopter_account_id'")) {
                    assertTrue(column.next()); assertEquals("bigint", column.getString(1)); assertEquals("YES", column.getString(2));
                }
                try (var sql = connection.createStatement(); var index = sql.executeQuery("select indexdef from pg_indexes where schemaname=current_schema() and indexname='idx_adoptions_owner_created'")) {
                    assertTrue(index.next()); assertTrue(index.getString(1).contains("(adopter_account_id, created_at DESC, id DESC)"));
                }
                try (var sql = connection.createStatement()) {
                    assertEquals("23503", assertThrows(SQLException.class, () -> sql.executeUpdate("update adoption_applications set adopter_account_id=999999 where id=1")).getSQLState());
                }
            }
            try (var context = new SpringApplicationBuilder(BackendApplication.class).web(WebApplicationType.NONE)
                    .profiles("postgres-migration").run("--spring.datasource.url=" + url,
                            "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                            "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.datasource.hikari.schema=" + schema,
                            "--spring.flyway.schemas=" + schema, "--spring.flyway.default-schema=" + schema,
                            "--spring.flyway.enabled=true", "--spring.flyway.target=10",
                            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                            "--spring.jpa.properties.hibernate.default_schema=" + schema)) {
                var accounts = context.getBean(UserAccountRepository.class);
                var adopter = TestAccounts.principal(accounts.findByEmail("owner@example.com").orElseThrow());
                var adoptions = context.getBean(AdoptionService.class);
                assertTrue(adoptions.mine(adopter).isEmpty());
                var request = new AdoptionApplicationRequest(); request.setCatId(1L);
                var created = adoptions.submitApplication(request, adopter);
                var stored = context.getBean(AdoptionApplicationRepository.class).findById(created.id()).orElseThrow();
                assertEquals(adopter.getAccountId(), stored.getAdopterAccount().getId());
                assertEquals("Existing owner", created.adopterName());
                assertEquals(created.id(), adoptions.mine(adopter).getFirst().id());
                assertEquals("Historical name", adoptions.getById(1L, TestAccounts.staff()).adopterName());
            }
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement()) {
                    assertEquals("23503", assertThrows(SQLException.class, () -> sql.executeUpdate("delete from user_accounts where id=1")).getSQLState());
                }
                try (var sql = connection.createStatement(); var row = sql.executeQuery("select count(*) from adoption_applications where adopter_account_id=1")) {
                    assertTrue(row.next()); assertEquals(1, row.getInt(1));
                }
            }
        } finally { latest.clean(); }
    }
}
