package com.pawtrack.backend.care.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.domain.AlertSeverity;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.domain.AlertType;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.domain.CareRecord;
import com.pawtrack.backend.care.domain.CareRecordType;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@com.pawtrack.backend.support.StaffRegression
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CatHealthTimelineIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @Autowired CatRepository cats;
    @Autowired HealthDataRepository observations;
    @Autowired AlertRepository alerts;
    @Autowired CareRecordRepository careRecords;

    @Test
    void timelineContainsAllEventKinds_andAssemblesDtoWithOpenInViewDisabled() throws Exception {
        assertEquals("false", environment.getProperty("spring.jpa.open-in-view"));
        Cat cat = cats.save(new Cat("Nori"));

        saveObservation(cat, OffsetDateTime.parse("2026-09-10T09:00:00Z"), "38.40", 5);
        saveAlert(cat, OffsetDateTime.parse("2026-09-10T10:00:00Z"));
        saveCareRecord(cat, OffsetDateTime.parse("2026-09-10T11:00:00Z"));

        mvc.perform(get("/api/cats/{catId}/health-timeline", cat.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catId").value(cat.getId()))
                .andExpect(jsonPath("$.catName").value("Nori"))
                .andExpect(jsonPath("$.order").value("NEWEST_FIRST"))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[0].eventKind").value("CARE_RECORD"))
                .andExpect(jsonPath("$.events[0].eventType").value("CHECKUP"))
                .andExpect(jsonPath("$.events[0].description").value("Temperature rechecked; Nori is resting comfortably."))
                .andExpect(jsonPath("$.events[1].eventKind").value("ALERT"))
                .andExpect(jsonPath("$.events[1].eventType").value("FEVER"))
                .andExpect(jsonPath("$.events[1].alertStatus").value("OPEN"))
                .andExpect(jsonPath("$.events[1].alertSeverity").value("HIGH"))
                .andExpect(jsonPath("$.events[2].eventKind").value("HEALTH_OBSERVATION"))
                .andExpect(jsonPath("$.events[2].eventType").value("VITALS"))
                .andExpect(jsonPath("$.events[2].temperatureC").value(38.4))
                .andExpect(jsonPath("$.events[2].activityLevel").value(5));
    }

    @Test
    void orderingIsDeterministicWhenEventsShareATimestamp() throws Exception {
        Cat cat = cats.save(new Cat("Tie breaker"));
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-10T12:00:00Z");
        HealthData firstObservation = saveObservation(cat, timestamp, "38.10", 3);
        HealthData secondObservation = saveObservation(cat, timestamp, "38.20", 4);
        saveCareRecord(cat, timestamp);
        saveAlert(cat, timestamp);

        String body = mvc.perform(get("/api/cats/{catId}/health-timeline", cat.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode events = json.readTree(body).get("events");

        assertEquals(4, events.size());
        assertEquals("ALERT", events.get(0).get("eventKind").asText());
        assertEquals("CARE_RECORD", events.get(1).get("eventKind").asText());
        assertEquals("HEALTH_OBSERVATION", events.get(2).get("eventKind").asText());
        assertEquals(secondObservation.getId(), events.get(2).get("sourceId").asLong());
        assertEquals(firstObservation.getId(), events.get(3).get("sourceId").asLong());
        assertTrue(events.get(2).get("sourceId").asLong() > events.get(3).get("sourceId").asLong());
    }

    @Test
    void nonexistentCatReturnsNormalNotFoundResponse() throws Exception {
        mvc.perform(get("/api/cats/{catId}/health-timeline", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cat not found: 999999"));
    }

    private HealthData saveObservation(
            Cat cat,
            OffsetDateTime occurredAt,
            String temperatureC,
            int activityLevel
    ) {
        HealthData observation = new HealthData();
        observation.setCat(cat);
        observation.setTs(occurredAt);
        observation.setTemperatureC(new BigDecimal(temperatureC));
        observation.setActivityLevel(activityLevel);
        return observations.saveAndFlush(observation);
    }

    private Alert saveAlert(Cat cat, OffsetDateTime occurredAt) {
        Alert alert = new Alert();
        alert.setCat(cat);
        alert.setType(AlertType.FEVER);
        alert.setSeverity(AlertSeverity.HIGH);
        alert.setStatus(AlertStatus.OPEN);
        alert.setMessage("High temperature detected.");
        alert.setCreatedAt(occurredAt);
        return alerts.saveAndFlush(alert);
    }

    private CareRecord saveCareRecord(Cat cat, OffsetDateTime occurredAt) {
        CareRecord record = new CareRecord();
        record.setCat(cat);
        record.setType(CareRecordType.CHECKUP);
        record.setNote("Temperature rechecked; Nori is resting comfortably.");
        record.setCreatedAt(occurredAt);
        return careRecords.saveAndFlush(record);
    }
}
