package com.pawtrack.backend.care.api;

import com.pawtrack.backend.support.TestAccounts;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.alert.domain.*;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.api.dto.AlertResolutionRequest;
import com.pawtrack.backend.care.domain.CareRecordType;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.care.service.AlertResolutionService;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.pawtrack.backend.support.StaffRegression
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:alert-resolution;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AlertResolutionIntegrationTest {
    private static final String BODY = """
            {"careType":"CHECKUP","note":"Temperature rechecked; resting comfortably."}
            """;
    @Autowired UserAccountRepository accounts;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @Autowired CatRepository cats;
    @Autowired AlertRepository alerts;
    @Autowired CareRecordRepository careRecords;
    @Autowired HealthDataRepository observations;
    @Autowired AdoptionApplicationRepository applications;
    @Autowired HealthDataService health;
    @Autowired AlertResolutionService resolution;
    @Autowired PlatformTransactionManager transactionManager;
    private Cat cat;

    @BeforeEach
    void setup() {
        careRecords.deleteAllInBatch();
        applications.deleteAllInBatch();
        alerts.deleteAllInBatch();
        observations.deleteAllInBatch();
        cats.deleteAllInBatch();
        cat = new Cat("Nori");
        cat.setHealthStatus(CatHealthStatus.UNDER_OBSERVATION);
        cat = cats.saveAndFlush(cat);
    }

    @Test
    void resolvesAtomicallyAndAddsLinkedTimelineRecordWithOpenInViewDisabled() throws Exception {
        assertEquals("false", environment.getProperty("spring.jpa.open-in-view"));
        Alert alert = alert(AlertType.FEVER);
        OffsetDateTime originalTime = alert.getCreatedAt();
        JsonNode result = resolve(alert.getId());
        assertEquals(cat.getId(), result.get("catId").asLong());
        assertEquals(alert.getId(), result.get("alertId").asLong());
        assertEquals("CLOSED", result.get("status").asText());
        assertNotNull(OffsetDateTime.parse(result.get("resolvedAt").asText()));
        Alert closed = alerts.findById(alert.getId()).orElseThrow();
        assertEquals(AlertStatus.CLOSED, closed.getStatus());
        assertNotNull(closed.getResolvedAt());
        assertEquals(originalTime.toInstant(), closed.getCreatedAt().toInstant());
        assertEquals(1, careRecords.count());
        var care = careRecords.findAll().getFirst();
        assertEquals(result.get("careRecordId").asLong(), care.getId());
        assertEquals(alert.getId(), care.getAlert().getId());
        assertEquals(cat.getId(), care.getCat().getId());
        assertEquals(CareRecordType.CHECKUP, care.getType());
        assertEquals("Temperature rechecked; resting comfortably.", care.getNote());
        assertEquals(closed.getResolvedAt().toInstant(), care.getCreatedAt().toInstant());
        mvc.perform(get("/api/cats/{id}/health-timeline", cat.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventKind").value("CARE_RECORD"))
                .andExpect(jsonPath("$.events[0].sourceId").value(care.getId()))
                .andExpect(jsonPath("$.events[0].relatedAlertId").value(alert.getId()))
                .andExpect(jsonPath("$.events[1].eventKind").value("ALERT"))
                .andExpect(jsonPath("$.events[1].alertStatus").value("CLOSED"));
    }

    @Test
    void lastAlertRestoresNormalAndAllowsAdoption() throws Exception {
        Alert alert = alert(AlertType.FEVER);
        apply(409);
        resolve(alert.getId());
        assertEquals(CatHealthStatus.NORMAL, statusOfCat());
        apply(201);
    }

    @Test
    void anotherOpenAlertKeepsObservationAndBlocksAdoption() throws Exception {
        Alert fever = alert(AlertType.FEVER);
        alert(AlertType.LOW_ACTIVITY);
        resolve(fever.getId());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, statusOfCat());
        assertTrue(alerts.existsByCatIdAndStatus(cat.getId(), AlertStatus.OPEN));
        apply(409);
    }

    @ParameterizedTest
    @CsvSource({"AVAILABLE,NORMAL,NORMAL", "AVAILABLE,UNDER_OBSERVATION,NORMAL", "AVAILABLE,SICK,SICK",
            "ADOPTED,NORMAL,NORMAL", "ADOPTED,UNDER_OBSERVATION,NORMAL", "ADOPTED,SICK,SICK"})
    void resolvesHealthIndependentlyOfAdoption(CatAdoptionStatus adoptionStatus,
                                              CatHealthStatus initialHealth, CatHealthStatus expectedHealth) throws Exception {
        cat.setAdoptionStatus(adoptionStatus);
        cat.setHealthStatus(initialHealth);
        cats.saveAndFlush(cat);
        resolve(alert(AlertType.FEVER).getId());
        assertEquals(expectedHealth, statusOfCat());
        assertEquals(adoptionStatus, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
        assertEquals(1, careRecords.count());
    }

    @Test
    void repeatedResolutionReturnsConflictWithoutAnotherRecordOrTimestampChange() throws Exception {
        Alert alert = alert(AlertType.FEVER);
        resolve(alert.getId());
        OffsetDateTime time = alerts.findById(alert.getId()).orElseThrow().getResolvedAt();
        mvc.perform(patch("/api/alerts/{id}/resolve", alert.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("refresh")));
        assertEquals(1, careRecords.count());
        assertEquals(time, alerts.findById(alert.getId()).orElseThrow().getResolvedAt());
    }

    @Test
    void legacyClosedAlertCannotBeResolvedAndDoesNotInventMetadata() throws Exception {
        Alert closed = alert(AlertType.FEVER);
        closed.setStatus(AlertStatus.CLOSED);
        alerts.saveAndFlush(closed);
        mvc.perform(patch("/api/alerts/{id}/resolve", closed.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        assertEquals(0, careRecords.count());
        assertNull(alerts.findById(closed.getId()).orElseThrow().getResolvedAt());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, statusOfCat());
    }

    @Test
    void feverBeforeResolutionDeduplicatesAndLaterFeverCreatesNewBlockingAlert() throws Exception {
        health.create(cat.getId(), null, new BigDecimal("40.0"), 1);
        health.create(cat.getId(), null, new BigDecimal("40.2"), 1);
        assertEquals(1, alerts.count());
        Alert first = alerts.findAll().getFirst();
        resolve(first.getId());
        assertEquals(CatHealthStatus.NORMAL, statusOfCat());
        health.create(cat.getId(), null, new BigDecimal("40.1"), 1);
        assertEquals(2, alerts.count());
        assertEquals(AlertStatus.CLOSED, alerts.findById(first.getId()).orElseThrow().getStatus());
        Alert open = alerts.findLatestOpenByCatAndType(cat.getId(), AlertType.FEVER).orElseThrow();
        assertNotEquals(first.getId(), open.getId());
        assertNull(open.getResolvedAt());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, statusOfCat());
        apply(409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"note\":\"Checked\"}", "{\"careType\":\"CHECKUP\"}",
            "{\"careType\":\"CHECKUP\",\"note\":\"   \"}",
            "{\"careType\":\"UNKNOWN\",\"note\":\"Checked\"}"})
    void invalidRequestsMakeNoChanges(String body) throws Exception {
        assertInvalid(body);
    }

    @Test
    void rejectsOversizedNote() throws Exception {
        assertInvalid(json.writeValueAsString(new AlertResolutionRequest(CareRecordType.CHECKUP, "x".repeat(2001))));
    }

    @Test
    void missingAlertReturnsNormalNotFound() throws Exception {
        mvc.perform(patch("/api/alerts/{id}/resolve", 999999L)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert not found: 999999"));
    }

    @Test
    void removedCloseEndpointCannotBypassWorkflow() throws Exception {
        Alert alert = alert(AlertType.FEVER);
        mvc.perform(patch("/api/alerts/{id}/close", alert.getId())).andExpect(status().isForbidden());
        assertEquals(AlertStatus.OPEN, alerts.findById(alert.getId()).orElseThrow().getStatus());
        assertEquals(0, careRecords.count());
    }

    @Test
    void rollbackRevertsAlertCareRecordAndCatTogether() {
        Alert alert = alert(AlertType.FEVER);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            resolution.resolve(alert.getId(), request());
            assertEquals(CatHealthStatus.NORMAL, statusOfCat());
            tx.setRollbackOnly();
        });
        Alert unchanged = alerts.findById(alert.getId()).orElseThrow();
        assertEquals(AlertStatus.OPEN, unchanged.getStatus());
        assertNull(unchanged.getResolvedAt());
        assertEquals(0, careRecords.count());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, statusOfCat());
    }

    @Test
    void competingStaffResolutionsCreateOnlyOneCareRecord() throws Exception {
        Alert alert = alert(AlertType.FEVER);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> compete(alert.getId(), ready, start));
            var second = executor.submit(() -> compete(alert.getId(), ready, start));
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(outcomes.contains(200));
            assertTrue(outcomes.contains(409));
        }
        assertEquals(1, careRecords.count());
        assertEquals(CatHealthStatus.NORMAL, statusOfCat());
    }

    private int compete(Long alertId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            resolution.resolve(alertId, request());
            return 200;
        } catch (ResponseStatusException ex) {
            return ex.getStatusCode().value();
        }
    }

    @Test
    void waitsForCatLockAndReadsAlertStateAfterThePreviousTransactionCommits() throws Exception {
        Alert alert = alert(AlertType.FEVER);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var owner = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                cats.findByIdForUpdate(cat.getId()).orElseThrow();
                locked.countDown();
                assertTrue(assertDoesNotThrow(() -> release.await(5, TimeUnit.SECONDS)));
                resolution.resolve(alert.getId(), request());
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var waiting = executor.submit(() -> compete(alert.getId(), attempting, new CountDownLatch(0)));
                assertTrue(attempting.await(5, TimeUnit.SECONDS));
                // A held Cat lock must block resolution even though Alert is not locked.
                assertThrows(TimeoutException.class, () -> waiting.get(250, TimeUnit.MILLISECONDS));
                release.countDown();
                owner.get(10, TimeUnit.SECONDS);
                assertEquals(409, waiting.get(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
        assertEquals(1, careRecords.count());
        assertEquals(CatHealthStatus.NORMAL, statusOfCat());
    }

    private void assertInvalid(String body) throws Exception {
        Alert alert = alert(AlertType.FEVER);
        mvc.perform(patch("/api/alerts/{id}/resolve", alert.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(AlertStatus.OPEN, alerts.findById(alert.getId()).orElseThrow().getStatus());
        assertNull(alerts.findById(alert.getId()).orElseThrow().getResolvedAt());
        assertEquals(0, careRecords.count());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, statusOfCat());
    }

    private Alert alert(AlertType type) {
        Alert alert = new Alert();
        alert.setCat(cat);
        alert.setType(type);
        alert.setCreatedAt(OffsetDateTime.parse("2026-01-01T12:00:00Z"));
        return alerts.saveAndFlush(alert);
    }

    private JsonNode resolve(Long alertId) throws Exception {
        return json.readTree(mvc.perform(patch("/api/alerts/{id}/resolve", alertId)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void apply(int expectedStatus) throws Exception {
        mvc.perform(post("/api/adoptions").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(TestAccounts.adopter(accounts, "alex@example.com"))).contentType(MediaType.APPLICATION_JSON).content("""
                        {"catId":%d,"adopterName":"Alex","adopterEmail":"alex@example.com"}
                        """.formatted(cat.getId())))
                .andExpect(status().is(expectedStatus));
    }

    private CatHealthStatus statusOfCat() { return cats.findById(cat.getId()).orElseThrow().getHealthStatus(); }

    private AlertResolutionRequest request() {
        return new AlertResolutionRequest(CareRecordType.CHECKUP, "Temperature rechecked.");
    }
}
