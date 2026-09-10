package com.pawtrack.backend.healthdata.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.domain.AlertType;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No outer test transaction: HTTP writes commit and repository assertions reload stored values.
@SpringBootTest(classes = BackendApplication.class,
        properties = "spring.datasource.url=jdbc:h2:mem:health-correctness;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthObservationCorrectnessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CatRepository cats;
    @Autowired HealthDataRepository observations;
    @Autowired AlertRepository alerts;
    @Autowired CareRecordRepository careRecords;
    @Autowired AdoptionApplicationRepository applications;
    private Long catId;

    @BeforeEach
    void setup() {
        careRecords.deleteAllInBatch();
        applications.deleteAllInBatch();
        alerts.deleteAllInBatch();
        observations.deleteAllInBatch();
        cats.deleteAllInBatch();
        catId = cats.saveAndFlush(new Cat("Nori")).getId();
    }

    @Test
    void equalTimestampNormalThenFeverUsesLaterIdAndBlocksAdoption() throws Exception {
        long first = record("38.00");
        long second = record("40.00");

        assertLatestAndReadOrder(second, first, "40.00");
        assertOpenFeverAndBlockedAdoption();
    }

    @Test
    void equalTimestampFeverThenNormalKeepsAlertUntilExplicitResolution() throws Exception {
        long first = record("40.00");
        long alertId = alerts.findAll().getFirst().getId();
        long second = record("38.00");

        assertLatestAndReadOrder(second, first, "38.00");
        assertOpenFeverAndBlockedAdoption();
        assertEquals(alertId, alerts.findAll().getFirst().getId());
        assertEquals(0, careRecords.count());
        assertNull(alerts.findById(alertId).orElseThrow().getResolvedAt());

        mvc.perform(patch("/api/alerts/{id}/resolve", alertId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"careType":"CHECKUP","note":"Temperature rechecked; resting comfortably."}
                                """))
                .andExpect(status().isOk());
        assertEquals(AlertStatus.CLOSED, alerts.findById(alertId).orElseThrow().getStatus());
        assertEquals(1, careRecords.count());
        assertEquals("NORMAL", cats.findById(catId).orElseThrow().getStatus());
        apply(201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"39.50", "39.51", "999.99", "-999.99"})
    void acceptedTemperatureIsReloadedAtExactPersistedPrecision(String temperature) throws Exception {
        long observationId = record(temperature);
        var stored = observations.findById(observationId).orElseThrow();
        assertEquals(new BigDecimal(temperature), stored.getTemperatureC());
        assertEquals(1, observations.count());
        boolean fever = new BigDecimal(temperature).compareTo(new BigDecimal("39.5")) > 0;
        assertEquals(fever ? 1 : 0, alerts.count());
        assertEquals(fever ? "UNDER_OBSERVATION" : "NORMAL", cats.findById(catId).orElseThrow().getStatus());
        if (fever) assertEquals(AlertStatus.OPEN, alerts.findAll().getFirst().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"39.501", "39.500", "1000.00", "-1000.00"})
    void unsupportedPrecisionOrCapacityReturns400WithoutWrites(String temperature) throws Exception {
        var before = cats.findById(catId).orElseThrow();
        mvc.perform(post("/api/cats/{id}/health", catId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperatureC\":" + temperature + ",\"activityLevel\":1}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, observations.count());
        assertEquals(0, alerts.count());
        var after = cats.findById(catId).orElseThrow();
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getUpdatedAt(), after.getUpdatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"activityLevel\":1}", "{\"temperatureC\":null,\"activityLevel\":1}"})
    void temperatureRemainsOptional(String body) throws Exception {
        mvc.perform(post("/api/cats/{id}/health", catId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertEquals(1, observations.count());
        assertNull(observations.findAll().getFirst().getTemperatureC());
        assertEquals(0, alerts.count());
        assertEquals("NORMAL", cats.findById(catId).orElseThrow().getStatus());
    }

    private long record(String temperature) throws Exception {
        var response = mvc.perform(post("/api/cats/{id}/health", catId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ts":"2026-09-10T09:00:00Z","temperatureC":%s,"activityLevel":1}
                                """.formatted(temperature)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("id").asLong();
    }

    private void assertLatestAndReadOrder(long latestId, long earlierId, String temperature) throws Exception {
        var latest = observations.findFirstByCatIdOrderByTsDescIdDesc(catId).orElseThrow();
        assertEquals(latestId, latest.getId());
        assertEquals(new BigDecimal(temperature), latest.getTemperatureC());
        mvc.perform(get("/api/cats/{id}/dashboard", catId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureC").value(Double.parseDouble(temperature)))
                .andExpect(jsonPath("$.hasActiveAlert").value(true));
        mvc.perform(get("/api/cats/{id}/health", catId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(latestId))
                .andExpect(jsonPath("$[1].id").value(earlierId));
        var response = mvc.perform(get("/api/cats/{id}/health-timeline", catId))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode timeline = json.readTree(response.getContentAsString());
        assertEquals("NEWEST_FIRST", timeline.get("order").asText());
        var observationIds = StreamSupport.stream(timeline.get("events").spliterator(), false)
                .filter(event -> "HEALTH_OBSERVATION".equals(event.get("eventKind").asText()))
                .map(event -> event.get("sourceId").asLong()).toList();
        assertEquals(List.of(latestId, earlierId), observationIds);
    }

    private void assertOpenFeverAndBlockedAdoption() throws Exception {
        assertEquals(1, alerts.count());
        var alert = alerts.findAll().getFirst();
        assertEquals(AlertStatus.OPEN, alert.getStatus());
        assertEquals(AlertType.FEVER, alert.getType());
        assertEquals("UNDER_OBSERVATION", cats.findById(catId).orElseThrow().getStatus());
        apply(409);
        assertEquals(0, applications.count());
    }

    private void apply(int expectedStatus) throws Exception {
        mvc.perform(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"catId":%d,"adopterName":"Alex","adopterEmail":"alex@example.com"}
                        """.formatted(catId)))
                .andExpect(status().is(expectedStatus));
    }
}
