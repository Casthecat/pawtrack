package com.pawtrack.backend.consistency;

import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.domain.AdoptionStatus;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.api.dto.AlertResolutionRequest;
import com.pawtrack.backend.care.domain.CareRecordType;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.care.service.AlertResolutionService;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real Spring/JPA transactions; only this run's UUID schema is created/cleaned. */
@EnabledIfEnvironmentVariable(named = "PAWTRACK_TEST_JDBC_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres-runtime")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatLockPostgresRuntimeIT {
    private static final String SCHEMA = "pawtrack_runtime_" + UUID.randomUUID().toString().replace("-", "");
    private static final AlertResolutionRequest CARE = new AlertResolutionRequest(CareRecordType.CHECKUP, "Rechecked by staff.");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PAWTRACK_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("PAWTRACK_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("PAWTRACK_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 5);
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.flyway.clean-disabled", () -> false);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.jpa.open-in-view", () -> false);
        properties.add("logging.level.org.hibernate.SQL", () -> "INFO");
    }

    @Autowired CatRepository cats;
    @Autowired AdoptionApplicationRepository applications;
    @Autowired AlertRepository alerts;
    @Autowired CareRecordRepository careRecords;
    @Autowired HealthDataRepository observations;
    @Autowired AdoptionService adoption;
    @Autowired AlertResolutionService resolution;
    @Autowired HealthDataService health;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private Long catId;

    @BeforeEach
    void setup() {
        assertEquals(SCHEMA, jdbc.queryForObject("select current_schema()", String.class));
        assertTrue(jdbc.queryForObject("select version()", String.class).startsWith("PostgreSQL"));
        careRecords.deleteAllInBatch();
        applications.deleteAllInBatch();
        alerts.deleteAllInBatch();
        observations.deleteAllInBatch();
        cats.deleteAllInBatch();
        catId = cats.saveAndFlush(new Cat("Lock verification")).getId();
    }

    @AfterAll
    static void cleanIsolatedSchema(@Autowired Flyway flyway) {
        assertArrayEquals(new String[]{SCHEMA}, flyway.getConfiguration().getSchemas());
        assertEquals(SCHEMA, flyway.getConfiguration().getDefaultSchema());
        flyway.clean();
    }

    @Test
    void competingApprovalsWaitForCatAndOnlyOneApplicationWins() throws Exception {
        Long first = submit("first@example.com");
        Long second = submit("second@example.com");
        assertEquals(List.of(200, 409), runWithCatLock(
                () -> adoption.approveApplication(first), () -> adoption.approveApplication(second)));
        assertEquals(AdoptionStatus.APPROVED, applications.findById(first).orElseThrow().getStatus());
        assertEquals(AdoptionStatus.REJECTED, applications.findById(second).orElseThrow().getStatus());
        assertEquals(1, applications.findAll().stream().filter(app -> app.getStatus() == AdoptionStatus.APPROVED).count());
        assertEquals("ADOPTED", cats.findById(catId).orElseThrow().getStatus());
    }

    @Test
    void competingResolutionsWaitForCatAndReadCommittedClosedState() throws Exception {
        Long alertId = fever();
        assertEquals(List.of(200, 409), runWithCatLock(
                () -> resolution.resolve(alertId, CARE), () -> resolution.resolve(alertId, CARE)));
        var closed = alerts.findById(alertId).orElseThrow();
        assertEquals(AlertStatus.CLOSED, closed.getStatus());
        assertNotNull(closed.getResolvedAt());
        assertEquals(1, careRecords.count());
        var care = careRecords.findAll().getFirst();
        assertEquals(alertId, care.getAlert().getId());
        assertEquals(catId, care.getCat().getId());
        assertEquals(closed.getResolvedAt(), care.getCreatedAt());
        assertEquals("NORMAL", cats.findById(catId).orElseThrow().getStatus());
    }

    @Test
    void healthWriteWaitsForResolutionThenCreatesANewOpenAlert() throws Exception {
        Long oldAlertId = fever();
        assertEquals(List.of(200, 200), runWithCatLock(
                () -> resolution.resolve(oldAlertId, CARE),
                () -> health.create(catId, OffsetDateTime.now(), new BigDecimal("40.00"), 1)));
        assertEquals(AlertStatus.CLOSED, alerts.findById(oldAlertId).orElseThrow().getStatus());
        assertEquals(2, observations.count());
        assertEquals(2, alerts.count());
        var open = alerts.findAll().stream().filter(alert -> alert.getStatus() == AlertStatus.OPEN).toList();
        assertEquals(1, open.size());
        assertNotEquals(oldAlertId, open.getFirst().getId());
        assertEquals(1, careRecords.count());
        assertEquals(oldAlertId, careRecords.findAll().getFirst().getAlert().getId());
        assertEquals("UNDER_OBSERVATION", cats.findById(catId).orElseThrow().getStatus());
    }

    private Long submit(String email) {
        var request = new AdoptionApplicationRequest();
        request.setCatId(catId);
        request.setAdopterName("Demo applicant");
        request.setAdopterEmail(email);
        return adoption.submitApplication(request).id();
    }

    private Long fever() {
        health.create(catId, OffsetDateTime.now().minusMinutes(1), new BigDecimal("40.00"), 1);
        return alerts.findAll().getFirst().getId();
    }

    private List<Integer> runWithCatLock(Runnable ownerAction, Runnable waiterAction) throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        var ownerPid = new AtomicInteger();
        var waiterPid = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var owner = executor.submit(() -> outcome(() -> {
                ownerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                cats.findByIdForUpdate(catId).orElseThrow();
                locked.countDown();
                await(release);
                ownerAction.run();
            }));
            try {
                assertTrue(locked.await(10, TimeUnit.SECONDS), "Owner must acquire Cat lock");
                var waiter = executor.submit(() -> outcome(() -> {
                    waiterPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    attempting.countDown();
                    waiterAction.run();
                }));
                assertTrue(attempting.await(10, TimeUnit.SECONDS));
                // The owner has locked ONLY Cat here. Observe the real database wait,
                // rather than assuming that two started threads overlap.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blocked;
                do {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                            "select ? = any(pg_blocking_pids(?))", Boolean.class, ownerPid.get(), waiterPid.get()));
                    if (blocked) break;
                    Thread.sleep(20);
                } while (System.nanoTime() < deadline);
                assertTrue(blocked, "PostgreSQL must report waiter blocked by the Cat lock owner");
                assertFalse(waiter.isDone(), "Waiter must not finish before Cat lock release");
                release.countDown();
                return List.of(owner.get(10, TimeUnit.SECONDS), waiter.get(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private int outcome(Runnable work) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.execute("set local lock_timeout = '10s'");
                work.run();
            });
            return 200;
        } catch (ResponseStatusException conflict) {
            return conflict.getStatusCode().value();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(15, TimeUnit.SECONDS), "Coordinator must release Cat lock");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
