package com.pawtrack.backend.identity.config;

import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Owns a fresh database, never the normal development database or its schemas. */
@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = "jdbc:postgresql:.+")
class DemoCloudPostgresIT {
    @TempDir Path uploads;

    @Test void disposableCloudStartsMigratesSeedsAndRestartsWithoutResetting() throws Exception {
        String adminUrl = System.getenv("PAWTRACK_TEST_JDBC_URL");
        String user = System.getenv("PAWTRACK_TEST_DB_USER");
        String password = System.getenv("PAWTRACK_TEST_DB_PASSWORD");
        URI address = URI.create(adminUrl.substring(5));
        String database = "pawtrack_portfolio_demo_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://" + address.getHost() + ":" + address.getPort() + "/" + database;
        try (var admin = DriverManager.getConnection(adminUrl, user, password); var sql = admin.createStatement()) {
            sql.execute("create database " + database);
            try {
                String[] args = {"--DB_HOST=" + address.getHost(), "--DB_PORT=" + address.getPort(),
                        "--DB_NAME=" + database, "--DB_USER=" + user, "--DB_PASSWORD=" + password,
                        "--PAWTRACK_DEMO_DATABASE_ACK=" + CloudDemoAccounts.ACK,
                        "--server.port=0", "--upload.path=" + uploads, "--logging.level.root=ERROR"};
                String firstHash;
                try (var context = start(args)) {
                    var accounts = context.getBean(UserAccountRepository.class);
                    assertEquals(3, accounts.count());
                    firstHash = accounts.findByEmail("staff@example.com").orElseThrow().getPasswordHash();
                    assertTrue(context.getBean(PasswordEncoder.class).matches("PawTrack-demo-staff!", firstHash));
                    assertTrue(firstHash.startsWith("{bcrypt}"));
                    assertEquals(6, context.getBean(CatRepository.class).count());
                    assertEquals("10", context.getBean(Flyway.class).info().current().getVersion().toString());
                    var client = HttpClient.newHttpClient();
                    String base = "http://127.0.0.1:" + context.getWebServer().getPort();
                    assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/cats/1")).build(),
                            HttpResponse.BodyHandlers.ofString()).statusCode());
                    assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health")).build(),
                            HttpResponse.BodyHandlers.ofString()).statusCode());
                    var csrfResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf"))
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "pawtrack-demo.example")
                            .header("Origin", "https://pawtrack-demo.example").build(), HttpResponse.BodyHandlers.ofString());
                    var json = context.getBean(ObjectMapper.class).readTree(csrfResponse.body());
                    String csrfCookie = csrfResponse.headers().firstValue("set-cookie").orElseThrow();
                    assertTrue(csrfCookie.toLowerCase().contains("secure"));
                    var login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "pawtrack-demo.example")
                            .header("Origin", "https://pawtrack-demo.example")
                            .header("Cookie", csrfCookie.split(";", 2)[0])
                            .header(json.get("headerName").asText(), json.get("token").asText())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("email=staff%40example.com&password=PawTrack-demo-staff%21"))
                            .build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, login.statusCode());
                    String sessionCookie = login.headers().allValues("set-cookie").stream()
                            .filter(value -> value.startsWith("JSESSIONID=")).findFirst().orElseThrow();
                    assertTrue(sessionCookie.contains("Secure"));
                    assertTrue(sessionCookie.contains("HttpOnly"));
                    assertTrue(sessionCookie.contains("SameSite=Lax"));
                    var noCsrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/cats"))
                            .header("Cookie", sessionCookie.split(";", 2)[0])
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Must not save\"}"))
                            .build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(403, noCsrf.statusCode());
                }
                try (var db = DriverManager.getConnection(url, user, password); var update = db.createStatement()) {
                    update.executeUpdate("update cats set name='Existing demo state' where name='Mochi'");
                }
                try (var restarted = start(args)) {
                    var accounts = restarted.getBean(UserAccountRepository.class);
                    assertEquals(3, accounts.count());
                    assertEquals(firstHash, accounts.findByEmail("staff@example.com").orElseThrow().getPasswordHash());
                    var cats = restarted.getBean(CatRepository.class);
                    assertEquals(6, cats.count());
                    assertTrue(cats.findAll().stream().anyMatch(cat -> cat.getName().equals("Existing demo state")));
                    assertEquals(0, restarted.getBean(Flyway.class).info().pending().length);
                }
            } finally {
                // Name is generated above, never accepted from environment/user input.
                sql.execute("drop database " + database + " with (force)");
            }
        }
    }

    private ServletWebServerApplicationContext start(String[] args) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(BackendApplication.class)
                .profiles("demo-cloud").run(args);
    }
}
